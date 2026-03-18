package com.liang.drugagent.domain.workflow;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EvidenceAssemblyResult {

    private List<EvidenceItem> flatItems = new ArrayList<>();
    private List<EvidenceGroup> groups = new ArrayList<>();
}
