package org.example.model;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class DMLResponse {

    private HashMap<Integer, Set<Integer>> dependencyGraph;
    private List<Integer> executionOrder;
    HashMap<Integer,String> sqlMapping;
    private Map<Integer,String> indexTableMap;
}
