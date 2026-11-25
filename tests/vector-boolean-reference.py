#!/usr/bin/env python3
"""Independent GEOS fixed-precision oracle, used for local qualification only.
Communicates one sheet at a time with the Java style generator."""
import sys,struct
import numpy as np
from shapely import Polygon, MultiPolygon, GeometryCollection, union_all, intersection, symmetric_difference, make_valid, is_valid, set_precision
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
while True:
    try:mode=integer()
    except EOFError:break
    try:
        if mode==0: shape=clip_shape(loops())
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
        if not shape.is_valid:raise RuntimeError(explain_validity(shape))
        rings=[]
        for p in polygons(shape):
            p=orient(p,sign=1);rings.extend([p.exterior,*p.interiors])
        out.write(struct.pack('>i',len(rings)))
        for ring in rings:
            a=np.asarray(ring.coords[:-1],dtype='>f4').reshape(-1);out.write(struct.pack('>i',a.size));out.write(a.tobytes())
        out.flush()
    except Exception as exc:
        import traceback;traceback.print_exc(file=sys.stderr);out.write(struct.pack('>i',-1));out.flush();break
