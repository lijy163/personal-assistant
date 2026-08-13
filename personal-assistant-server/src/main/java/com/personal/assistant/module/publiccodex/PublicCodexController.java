package com.personal.assistant.module.publiccodex;

import com.personal.assistant.common.response.ApiResponse;
import com.personal.assistant.module.publiccodex.PublicCodexDtos.AnswerResponse;
import com.personal.assistant.module.publiccodex.PublicCodexDtos.AskRequest;
import com.personal.assistant.module.publiccodex.PublicCodexDtos.AskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/codex")
public class PublicCodexController {
    private final PublicCodexService service;

    public PublicCodexController(PublicCodexService service) {
        this.service = service;
    }

    @PostMapping("/questions")
    public ApiResponse<AskResponse> ask(@RequestHeader("X-Public-Session") String sessionToken,
                                        @Valid @RequestBody AskRequest request) {
        return ApiResponse.success(service.ask(sessionToken, request.question()));
    }

    @GetMapping("/questions/{taskId}")
    public ApiResponse<AnswerResponse> answer(@RequestHeader("X-Public-Session") String sessionToken,
                                              @PathVariable Long taskId) {
        return ApiResponse.success(service.answer(sessionToken, taskId));
    }
}
