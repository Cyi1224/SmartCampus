package com.simon.campus.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class QueryExpansion {
    private String mainQuery;
    private List<String> subQueries;
    private List<String> keywords;
}
