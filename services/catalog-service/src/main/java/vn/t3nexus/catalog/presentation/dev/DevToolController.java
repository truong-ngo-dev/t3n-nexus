package vn.t3nexus.catalog.presentation.dev;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dev")
public class DevToolController {

    @PostMapping(value = "/minio-upload-cmd", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> minioUploadCmd(@RequestBody UploadCmdRequest request) {
        String cmd = "curl.exe -X PUT \"%s\" --upload-file \"%s\"".formatted(request.uploadUrl(), request.filePath());
        return ResponseEntity.ok(cmd);
    }

    public record UploadCmdRequest(String uploadUrl, String filePath) {}
}
