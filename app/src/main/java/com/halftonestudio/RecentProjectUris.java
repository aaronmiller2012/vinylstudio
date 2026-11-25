package com.halftonestudio;
import java.util.LinkedHashSet;
import java.util.Set;

/** Android-independent recent SAF project history, including the RC81 literal \n migration. */
public final class RecentProjectUris {
    private RecentProjectUris() {}
    public static Set<String> kept(String history) {
        LinkedHashSet<String> result=new LinkedHashSet<>();
        if(history==null)return result;
        for(String uri:history.replace("\\n","\n").split("\n")){
            String value=uri.trim();
            if(!value.isEmpty()&&value.startsWith("content://")&&result.size()<5)result.add(value);
        }
        return result;
    }
    public static String remember(String current,String previous){
        LinkedHashSet<String> entries=new LinkedHashSet<>();
        if(current!=null&&!current.trim().isEmpty())entries.add(current.trim());
        if(previous!=null)for(String old:previous.replace("\\n","\n").split("\n")){
            if(entries.size()>=5)break;
            if(!old.trim().isEmpty())entries.add(old.trim());
        }
        return String.join("\n",entries);
    }
}
