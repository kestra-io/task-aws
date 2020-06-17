package org.kestra.task.aws.s3;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.annotations.OutputProperty;
import org.kestra.core.models.executions.metrics.Counter;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.runners.RunContext;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.File;
import java.net.URI;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Example(
    code = {
        "bucket: \"my-bucket\"",
        "key: \"path/to/file\""
    }
)
@Documentation(
    description = "Download a file to a S3 bucket."
)
public class Download extends AbstractS3Object implements RunnableTask<Download.Output> {
    @InputProperty(
        description = "The bucket where to download the file",
        dynamic = true
    )
    private String bucket;

    @InputProperty(
        description = "The key where to download the file",
        dynamic = true
    )
    private String key;

    @InputProperty(
        description = "VersionId used to reference a specific version of the object.",
        dynamic = true
    )
    protected String versionId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String bucket = runContext.render(this.bucket);
        String key = runContext.render(this.key);
        File tempFile = File.createTempFile("download_", ".s3");
        //noinspection ResultOfMethodCallIgnored
        tempFile.delete();

        try (S3Client client = this.client(runContext)) {
            GetObjectRequest.Builder builder = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key);

            if (this.versionId != null) {
                builder.versionId(runContext.render(this.versionId));
            }

            if (this.requestPayer != null) {
                builder.requestPayer(runContext.render(this.requestPayer));
            }

            GetObjectResponse response = client.getObject(
                builder.build(),
                ResponseTransformer.toFile(tempFile)
            );

            runContext.metric(Counter.of("file.size", response.contentLength()));

            return Output
                .builder()
                .uri(runContext.putTempFile(tempFile))
                .eTag(response.eTag())
                .contentLength(response.contentLength())
                .contentType(response.contentType())
                .metadata(response.metadata())
                .versionId(response.versionId())
                .build();
        }
    }

    @SuperBuilder
    @Getter
    public static class Output extends ObjectOutput implements org.kestra.core.models.tasks.Output {
        private final URI uri;

        @OutputProperty(
            description = "Size of the body in bytes."
        )
        private final Long contentLength;

        @OutputProperty(
            description = "A standard MIME type describing the format of the object data."
        )
        private final String contentType;

        @OutputProperty(
            description = "A map of metadata to store with the object in S3."
        )
        private final Map<String, String> metadata;
    }
}