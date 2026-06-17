package org.aiincubator.ilmai.agent.service;

import lombok.RequiredArgsConstructor;
import org.aiincubator.ilmai.materials.MaterialDto;
import org.aiincubator.ilmai.materials.MaterialsApi;
import org.aiincubator.ilmai.plan.LearningPlanDto;
import org.aiincubator.ilmai.plan.PlanStepDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlanViewFactory {

    private final MaterialsApi materialsApi;

    public PlanView toView(UUID userId, LearningPlanDto plan, List<PlanStepDto> steps) {
        Map<UUID, String> names = materialNames(userId);
        return new PlanView(true, plan.getGoal(), plan.getTargetDate(), toStepViews(steps, names),
                plan.isReplanNeeded());
    }

    private List<PlanStepView> toStepViews(List<PlanStepDto> steps, Map<UUID, String> names) {
        List<PlanStepView> views = new ArrayList<>();
        for (PlanStepDto step : steps) {
            views.add(new PlanStepView(
                    step.getDayIndex(),
                    step.getScheduledDate(),
                    step.getTitle(),
                    step.getActivity() == null ? null : step.getActivity().name().toLowerCase(Locale.ROOT),
                    materialTitles(step.getMaterialIds(), names),
                    step.getNote(),
                    step.isDone()));
        }
        return views;
    }

    private List<String> materialTitles(List<UUID> materialIds, Map<UUID, String> names) {
        List<String> titles = new ArrayList<>();
        if (materialIds != null) {
            for (UUID id : materialIds) {
                String title = names.get(id);
                if (title != null) {
                    titles.add(title);
                }
            }
        }
        return titles;
    }

    private Map<UUID, String> materialNames(UUID userId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (MaterialDto material : materialsApi.findReadyForUser(userId)) {
            names.put(material.getId(), material.getTitle());
        }
        return names;
    }
}
