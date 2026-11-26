#!/usr/bin/env python3
"""Independent GEOS fixed-precision oracle, used for local qualification only.
Communicates one sheet at a time with the Java style generator."""
import sys,struct
import numpy as np
from shapely import box, Polygon, MultiPolygon, GeometryCollection, union_all, intersection, symmetric_difference, make_valid, is_valid, set_precision
from shapely.geometry.polygon import orient
from shapely.validation import explain_validity
GRID=1/1024
inp=sys.stdin.buffer;out=sys.stdout.buffer
def read(n):
    d=inp.read(n)
    if len(d)!=n: raise EOFError()
    return d
def integer(): return struct.unpack('>i',read(4))[0]
def loops():
    result=[]
    for _ in range(integer()):
        size=integer()
        result.append(np.frombuffer(read(4*size),dtype='>f4').astype('float64').reshape(-1,2))
    return result
def polygons(g):
    if g.is_empty:return []
    if g.geom_type=='Polygon':return [g]
    if hasattr(g,'geoms'):return [p for o in g.geoms for p in polygons(o)]
    return []
def polygon(l):
    p=Polygon(l[0],l[1:]);return set_precision(make_valid(p) if not p.is_valid else p,GRID)
def clip_shape(l):
    if not l:return Polygon()
    a=[polygon([x]) for x in l]
    def xor(lo,hi):
        if hi-lo==1:return a[lo]
        m=(lo+hi)//2;return symmetric_difference(xor(lo,m),xor(m,hi),grid_size=GRID)
    return xor(0,len(a))
def polygonal(g):
    return union_all(polygons(g),grid_size=GRID) if not g.is_empty else Polygon()
def condition(shape,clip,close_r,open_r):
    # Float precision for intermediate buffers, snap only the output.
    shape=set_precision(shape,0);clip=set_precision(clip,0)
    shape=intersection(shape.buffer(close_r,quad_segs=5).buffer(-close_r,quad_segs=5),clip)
    shape=shape.buffer(-open_r,quad_segs=5).buffer(open_r,quad_segs=5)
    probe=max(.018,close_r-.025)
    for _ in range(3):
        thin=shape.buffer(probe,quad_segs=5).buffer(-probe,quad_segs=5).difference(shape)
        parts=[p for p in polygons(thin) if not p.buffer(-.009).is_empty]
        if not parts:break
        shape=shape.difference(union_all(parts).buffer(close_r,quad_segs=5))
        shape=shape.buffer(-open_r,quad_segs=5).buffer(open_r,quad_segs=5)
    return set_precision(intersection(shape,clip),GRID)
while True:
    try:mode=integer()
    except EOFError:break
    consumed=False
    try:
        if mode==0: shape=clip_shape(loops())
        elif mode==4:
            raw=loops();snip=loops();windows=[struct.unpack('>ffff',read(16)) for _ in range(integer())]
            close_r,open_r=struct.unpack('>ff',read(8));consumed=True
            shape=clip_shape(raw);clip=clip_shape(snip)
            core=union_all([box(*r) for r in windows],grid_size=GRID)
            halo=8*(close_r+open_r)+.1
            region=union_all([box(r[0]-halo,r[1]-halo,r[2]+halo,r[3]+halo) for r in windows],grid_size=GRID)
            if region.area>.30*box(*clip.bounds).area:raise ValueError('window halo budget')
            local=intersection(shape,region,grid_size=GRID)
            if local.is_empty or sum(len(p.exterior.coords)+sum(len(h.coords) for h in p.interiors) for p in polygons(local))>60000:raise ValueError('local complexity budget')
            fixed=condition(local,intersection(clip,region,grid_size=GRID),close_r,open_r)
            shape=polygonal(intersection(polygonal(union_all([polygonal(shape.difference(core)),polygonal(intersection(fixed,core,grid_size=GRID))],grid_size=GRID)),clip,grid_size=GRID))
        elif mode==3:
            shape=clip_shape(loops());clip=clip_shape(loops())
        else:
            n=integer();motifs=[polygon(loops()) for _ in range(n)]
            vin,waste=struct.unpack('>ff',read(8));clip=clip_shape(loops())
            shape=union_all(motifs,grid_size=GRID)
            keep=[]
            for p in polygons(shape):
                if p.area<vin:continue
                holes=[h.coords for h in p.interiors if Polygon(h).area>=waste]
                keep.append(Polygon(p.exterior.coords,holes))
            shape=union_all(keep,grid_size=GRID)
            shape=intersection(shape,clip,grid_size=GRID)
        if mode in (2,3):
            close_r,open_r=struct.unpack('>ff',read(8));consumed=True
            # Independent morphological candidate, not a reproduction of JTS internals.
            shape=intersection(shape.buffer(close_r,quad_segs=5).buffer(-close_r,quad_segs=5),clip)
            shape=shape.buffer(-open_r,quad_segs=5).buffer(open_r,quad_segs=5)
            probe=max(.018,close_r-.025)
            for _ in range(3):
                thin=shape.buffer(probe,quad_segs=5).buffer(-probe,quad_segs=5).difference(shape)
                parts=[p for p in polygons(thin) if not p.buffer(-.009).is_empty]
                if not parts:break
                shape=shape.difference(union_all(parts).buffer(close_r,quad_segs=5))
                shape=shape.buffer(-open_r,quad_segs=5).buffer(open_r,quad_segs=5)
            shape=set_precision(intersection(shape,clip),GRID)
        if not shape.is_valid:raise RuntimeError(explain_validity(shape))
        rings=[]
        for p in polygons(shape):
            p=orient(p,sign=1);rings.extend([p.exterior,*p.interiors])
        out.write(struct.pack('>i',len(rings)))
        for ring in rings:
            a=np.asarray(ring.coords[:-1],dtype='>f4').reshape(-1);out.write(struct.pack('>i',a.size));out.write(a.tobytes())
        out.flush()
    except Exception as exc:
        import traceback;traceback.print_exc(file=sys.stderr);out.write(struct.pack('>i',-1));out.flush()
        if not consumed:break
