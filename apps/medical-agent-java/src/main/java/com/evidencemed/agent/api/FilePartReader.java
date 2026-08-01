package com.evidencemed.agent.api;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class FilePartReader {
    public Mono<byte[]> read(FilePart file) {
        if (file == null) return Mono.just(new byte[0]);
        return DataBufferUtils.join(file.content()).map(buffer -> {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            DataBufferUtils.release(buffer);
            return bytes;
        });
    }
}
