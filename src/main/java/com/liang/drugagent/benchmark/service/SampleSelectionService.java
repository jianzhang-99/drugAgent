package com.liang.drugagent.benchmark.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liang.drugagent.agent.common.entity.TaskCard;
import com.liang.drugagent.agent.common.mapper.TaskCardMapper;
import com.liang.drugagent.benchmark.entity.ManualReviewSample;
import com.liang.drugagent.benchmark.mapper.ManualReviewSampleMapper;
import com.liang.drugagent.scene.tender_review.model.RuleHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽样策略服务。
 *
 * <p>实现三种抽样策略：
 * <ul>
 *   <li>随机抽样：从历史case中按比例随机抽取</li>
 *   <li>高偏差优先抽样：LLM高置信度但结论可疑的case</li>
 *   <li>高风险边界抽样：评分靠前但未达阈值的case</li>
 * </ul>
 *
 * <p>该服务仅负责样本选择，不负责人工审核流程。
 */
@Slf4j
@Service
public class SampleSelectionService {

    /** 随机抽样标识 */
    public static final String STRATEGY_RANDOM = "RANDOM";
    /** 高偏差优先抽样标识 */
    public static final String STRATEGY_HIGH_DEVIATION = "HIGH_DEVIATION";
    /** 高风险边界抽样标识 */
    public static final String STRATEGY_HIGH_RISK_BOUNDARY = "HIGH_RISK_BOUNDARY";

    private final TaskCardMapper taskCardMapper;
    private final ManualReviewSampleMapper sampleMapper;

    public SampleSelectionService(TaskCardMapper taskCardMapper,
                                  ManualReviewSampleMapper sampleMapper) {
        this.taskCardMapper = taskCardMapper;
        this.sampleMapper = sampleMapper;
    }

    /**
     * 执行抽样。
     *
     * @param strategy  抽样策略
     * @param sampleSize 抽样数量
     * @return 抽出的样本列表
     */
    public List<ManualReviewSample> selectSamples(String strategy, int sampleSize) {
        log.info("[抽样服务] 开始抽样: 策略={}, 数量={}", strategy, sampleSize);

        List<TaskCard> candidates = loadCompletedCases();

        if (candidates.isEmpty()) {
            log.warn("[抽样服务] 无可抽样的历史case");
            return Collections.emptyList();
        }

        List<ManualReviewSample> selected;
        switch (strategy) {
            case STRATEGY_RANDOM -> selected = randomSelection(candidates, sampleSize);
            case STRATEGY_HIGH_DEVIATION -> selected = highDeviationSelection(candidates, sampleSize);
            case STRATEGY_HIGH_RISK_BOUNDARY -> selected = highRiskBoundarySelection(candidates, sampleSize);
            default -> {
                log.error("[抽样服务] 未知抽样策略: {}", strategy);
                return Collections.emptyList();
            }
        }

        // 持久化样本记录
        for (ManualReviewSample sample : selected) {
            sampleMapper.insert(sample);
        }

        log.info("[抽样服务] 抽样完成: 策略={}, 实际抽取={}", strategy, selected.size());
        return selected;
    }

    /**
     * 随机抽样：从已完成case中随机选取。
     */
    private List<ManualReviewSample> randomSelection(List<TaskCard> candidates, int sampleSize) {
        List<TaskCard> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled);
        int count = Math.min(sampleSize, shuffled.size());

        return shuffled.subList(0, count).stream()
                .map(card -> buildSample(card, STRATEGY_RANDOM, null))
                .collect(Collectors.toList());
    }

    /**
     * 高偏差优先抽样：选择LLM置信度高但风险评分处于边界的case。
     *
     * <p>这类case的特征是LLM给出了高置信度判断，但综合评分处于阈值附近，
     * 说明规则融合或LLM判断可能存在不确定性。
     */
    private List<ManualReviewSample> highDeviationSelection(List<TaskCard> candidates, int sampleSize) {
        // 筛选置信度较高但评分在边界区间的case（阈值附近：50-80分）
        List<TaskCard> filtered = candidates.stream()
                .filter(card -> {
                    Integer score = card.getScore();
                    return score != null && score >= 50 && score < 80;
                })
                .sorted(Comparator.comparing(TaskCard::getScore).reversed())
                .collect(Collectors.toList());

        int count = Math.min(sampleSize, filtered.size());
        return filtered.subList(0, count).stream()
                .map(card -> buildSample(card, STRATEGY_HIGH_DEVIATION, null))
                .collect(Collectors.toList());
    }

    /**
     * 高风险边界抽样：选择评分靠前但未达阈值的case。
     *
     * <p>这类case虽然排名靠前，但未达到高风险阈值，是潜在的漏报case，
     * 需要人工确认是否存在系统未识别的风险模式。
     */
    private List<ManualReviewSample> highRiskBoundarySelection(List<TaskCard> candidates, int sampleSize) {
        // 筛选评分在40-80分区间的case（高风险边界）
        List<TaskCard> filtered = candidates.stream()
                .filter(card -> {
                    Integer score = card.getScore();
                    return score != null && score >= 40 && score < 80;
                })
                .sorted(Comparator.comparing(TaskCard::getScore).reversed())
                .limit(sampleSize * 3) // 预留更多候选
                .collect(Collectors.toList());

        int count = Math.min(sampleSize, filtered.size());
        return filtered.subList(0, count).stream()
                .map(card -> buildSample(card, STRATEGY_HIGH_RISK_BOUNDARY, null))
                .collect(Collectors.toList());
    }

    /**
     * 加载所有已完成的标书审查case。
     */
    private List<TaskCard> loadCompletedCases() {
        LambdaQueryWrapper<TaskCard> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskCard::getTaskType, "TENDER_REVIEW")
                .eq(TaskCard::getIsDeleted, 0)
                .eq(TaskCard::getStatus, "COMPLETED");
        return taskCardMapper.selectList(queryWrapper);
    }

    /**
     * 构建样本对象。
     *
     * @param card           关联的任务卡片
     * @param strategy       使用的抽样策略
     * @param llmConfidence LLM置信度（可为null）
     */
    private ManualReviewSample buildSample(TaskCard card, String strategy, Double llmConfidence) {
        return ManualReviewSample.builder()
                .sampleId(UUID.randomUUID().toString())
                .caseId(card.getCaseId())
                .selectionStrategy(strategy)
                .llmConfidence(llmConfidence)
                .riskScore(card.getScore())
                .riskLevel(card.getRiskLevel())
                .reviewStatus("PENDING")
                .systemResult(card.getSummary())
                .build();
    }

    /**
     * 根据样本ID查询样本详情。
     */
    public Optional<ManualReviewSample> findBySampleId(String sampleId) {
        LambdaQueryWrapper<ManualReviewSample> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ManualReviewSample::getSampleId, sampleId);
        return Optional.ofNullable(sampleMapper.selectOne(queryWrapper));
    }

    /**
     * 查询待审核样本列表。
     */
    public List<ManualReviewSample> findPendingSamples() {
        LambdaQueryWrapper<ManualReviewSample> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ManualReviewSample::getReviewStatus, "PENDING")
                .orderByAsc(ManualReviewSample::getCreatedAt);
        return sampleMapper.selectList(queryWrapper);
    }
}
