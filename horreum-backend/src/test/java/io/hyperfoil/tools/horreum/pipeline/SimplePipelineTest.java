package io.hyperfoil.tools.horreum.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.horreum.pipeline.steps.LoggingStep;
import io.hyperfoil.tools.horreum.pipeline.steps.utils.ObjectMappingStep;
import io.hyperfoil.tools.horreum.pipeline.steps.actions.EmailAction;
import io.hyperfoil.tools.horreum.pipeline.steps.actions.HttpAction;
import io.hyperfoil.tools.horreum.pipeline.steps.actions.SlackAction;
import io.hyperfoil.tools.horreum.svc.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SimplePipelineTest {
    static ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void simpleLoggingPipelineTest() {

        Pipeline.Builder builder = new Pipeline.Builder();

        builder.addStep(new LoggingStep("Simple log"));

        builder.build().run();

    }

    @Test
    public void simpleJsonProcessingPipelineTest() throws JsonProcessingException {

        String payload = """
                { "msg" : "This is a test!"}
                """;

        Pipeline.Builder builder = new Pipeline.Builder();

        builder.addStep(new ObjectMappingStep(payload));
        builder.addStep(new LoggingStep("Simple log"));

        builder.build().run();

    }

    @Test
    public void emailActionConfigurationTest() throws JsonProcessingException {
        String config = """
            { 
                "user": "johara",
                "formatter": "json"
            }
                """;
        String secrets = """
                {}                
                """;

        EmailAction emailActionStep = new EmailAction(
                objectMapper.readTree(config),
                objectMapper.readTree(secrets)
        );


        emailActionStep.validate();

    }

    @Test
    public void emailActionIncorrectConfigurationTest() throws JsonProcessingException {
        String config = """
            { 
                "user": "johara"
            }
                """;
        String secrets = """
                {}                
                """;

        EmailAction emailActionStep = new EmailAction(
                objectMapper.readTree(config),
                objectMapper.readTree(secrets)
        );


        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> emailActionStep.validate());
        thrown.getMessage().contains("Configuration is missing property 'formatter'");
    }

    @Test
    public void httpActionInvalidUrlTest() throws JsonProcessingException {

        String config = """
                { 
                    "url": "http://example.com"
                }
                    """;
        String secrets = "{}";

        HttpAction httpAction = new HttpAction(
                objectMapper.readTree(config),
                objectMapper.readTree(secrets)
        );

        //Can use this to validate configuration before
        //persisting the action configuration

        ServiceException thrown = assertThrows(ServiceException.class, () -> httpAction.validate());
        thrown.getMessage().contains("not on the list of allowed URL prefixes");

    }

    @Test
    public void emailActionPipelineTest() throws JsonProcessingException {

        String payload = """
                { "msg" : "This is a test!"}
                """;

        String config = """
            { 
                "user": "johara",
                "formatter": "json"
            }
                """;
        String secrets = """
                {}                
                """;

        EmailAction emailActionStep = new EmailAction(
                objectMapper.readTree(config),
                objectMapper.readTree(secrets)
        );

        //Can use this to validate configuration before
        //persisting the action configuration
        emailActionStep.validate();

        Pipeline.Builder builder = new Pipeline.Builder();

        builder.addStep(new ObjectMappingStep(payload));
        builder.addStep(emailActionStep);

        builder.build().run();

    }

    @Test
    public void slackActionPipelineTest() throws JsonProcessingException {

        ObjectMapper objectMapper = new ObjectMapper();

        String payload = """
                { "msg" : "This is a test!"}
                """;

        String config = """
            { 
                "formatter": "json",
                "channel": "#slack-test-channel"
            }
                """;
        String secrets = """
                {
                "token": "xoxb-123456789012-1234567890123-12345678901234567890abcdef1234567890abcdef"          
                }      
                """;

        SlackAction slackActionStep = new SlackAction(
                objectMapper.readTree(config),
                objectMapper.readTree(secrets)
        );

        //Can use this to validate configuration before
        //persisting the action configuration
        slackActionStep.validate();

        Pipeline.Builder builder = new Pipeline.Builder();

        builder.addStep(new ObjectMappingStep(payload));
        builder.addStep(slackActionStep);

        builder.build().run();

    }


}
