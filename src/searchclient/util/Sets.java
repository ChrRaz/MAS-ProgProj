package searchclient.util;

import java.util.HashSet;
import java.util.Set;


public class Sets {

    public static <T> Set<T> intersection(Set<T> s1, Set<T> s2){
        Set<T> temp1 = new HashSet<>(s1);
        temp1.retainAll(s2);
        return temp1;
    }
    
}