package com.evidencemed.agent.api;

import com.evidencemed.agent.application.harness.HarnessRequest;
import com.evidencemed.agent.application.harness.HarnessResponse;
import com.evidencemed.agent.application.harness.MedicalAgentHarness;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/consultations")
public class ConsultationController {
    private final MedicalAgentHarness harness;
    private final FilePartReader files;

    public ConsultationController(MedicalAgentHarness harness, FilePartReader files) {
        this.harness = harness;
        this.files = files;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<HarnessResponse> consult(Mono<Principal> principal,
                                         @RequestPart("question") String question,
                                         @RequestPart(value = "sessionId", required = false) String sessionId,
                                         @RequestPart(value = "image", required = false) FilePart image) {
        Mono<ImageInput> imageInput = image == null ? Mono.just(ImageInput.empty()) : readImage(image);
        return Mono.zip(principal, imageInput)
                .flatMap(tuple -> Mono.fromCallable(() -> harness.run(tuple.getT1().getName(),
                                new HarnessRequest(sessionId, question, tuple.getT2().bytes(), tuple.getT2().mediaType())))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private Mono<ImageInput> readImage(FilePart file) {
        String mediaType = file.headers().getContentType() == null
                ? "application/octet-stream" : file.headers().getContentType().toString();
        return files.read(file).map(bytes -> new ImageInput(bytes, mediaType));
    }

    private record ImageInput(byte[] bytes, String mediaType) {
        private static ImageInput empty() { return new ImageInput(null, null); }
    }
}
