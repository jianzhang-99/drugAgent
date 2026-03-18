package com.liang.drugagent.domain.workflow;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EvidenceGroup {

    private String groupKey;
    private String title;
    private String summary;
    private String source;
    private List<EvidenceItem> items = new ArrayList<>();
}
