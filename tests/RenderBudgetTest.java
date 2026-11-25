package com.halftonestudio;

/** Deterministic host test for byte-budgeted immutable render snapshot caching. */
public final class RenderBudgetTest {
    public static void main(String[] args){
        RenderPreparationCache<String,byte[]> budget=new RenderPreparationCache<>(2,10L,v->v.length);
        budget.put("a",new byte[5]);budget.put("b",new byte[4]);
        if(budget.size()!=2||budget.retainedBytes()!=9)throw new AssertionError("retains 9 bytes");
        budget.get("a");budget.put("c",new byte[5]);
        if(budget.get("b")!=null||budget.get("a")==null||budget.get("c")==null)throw new AssertionError("LRU order");
        if(budget.retainedBytes()!=10)throw new AssertionError("eviction budget, size="+budget.retainedBytes());
        budget.put("oversized",new byte[11]);
        if(budget.get("oversized")!=null||budget.retainedBytes()!=10)throw new AssertionError("oversized bypass");
        budget.put("a",new byte[3]);
        if(budget.size()!=2||budget.retainedBytes()!=8)throw new AssertionError("overwrite accounting");
        budget.clear();if(budget.size()!=0||budget.retainedBytes()!=0)throw new AssertionError("clear");
        RenderPreparationCache<String,String> compatibility=new RenderPreparationCache<>(1);
        compatibility.put("one","test");compatibility.put("two","pass");
        if(compatibility.size()!=1||compatibility.get("one")!=null)throw new AssertionError("legacy bound");
        System.out.println("PASS: specialty bitmap cache byte budgets and LRU");
    }
}
