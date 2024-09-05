package org.example.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Data
public class DependencyGraph {

    private HashMap<Integer, Set<Integer>> dependencyGraph;
    private HashMap<Integer,String> sqlMapping;
    private Map<Integer,String> indexTargetTableMap;
}
