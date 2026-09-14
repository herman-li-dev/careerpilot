package com.hermanli.careerpilot.controller.careerpilot;

import com.hermanli.careerpilot.api.ApiResponse;
import com.hermanli.careerpilot.identity.CurrentUserId;
import com.hermanli.careerpilot.plan.CareerPlan;
import com.hermanli.careerpilot.plan.PlanService;
import com.hermanli.careerpilot.plan.PlanTask;
import com.hermanli.careerpilot.plan.UpdatePlanTaskRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/{planId}")
    public ApiResponse<CareerPlan> get(@PathVariable long planId, @CurrentUserId long userId) {
        return ApiResponse.success(planService.get(userId, planId));
    }

    @GetMapping("/{planId}/tasks")
    public ApiResponse<List<PlanTask>> listTasks(@PathVariable long planId, @CurrentUserId long userId) {
        return ApiResponse.success(planService.listTasks(userId, planId));
    }

    @PatchMapping("/{planId}/tasks/{taskId}")
    public ApiResponse<PlanTask> updateTask(
            @PathVariable long planId,
            @PathVariable long taskId,
            @CurrentUserId long userId,
            @Valid @RequestBody UpdatePlanTaskRequest request
    ) {
        return ApiResponse.success(planService.updateTask(userId, planId, taskId, request));
    }

    @PostMapping("/{planId}/regenerate-remaining")
    public ApiResponse<List<PlanTask>> regenerateRemaining(
            @PathVariable long planId,
            @CurrentUserId long userId
    ) {
        return ApiResponse.success(planService.regenerateRemaining(userId, planId));
    }
}
