package usace.cc.plugin.hmsrunner;

import usace.cc.plugin.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.HashMap;

import java.net.URI;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

public class pushOutputsAction {
    private Action action;
    private Payload mp;
    private PluginManager pm;
    private String modelOutputDestination;

    public pushOutputsAction(Action a, Payload mp, PluginManager pm, String modelOutputDestination) {
        this.action = a;
        this.mp = mp;
        this.pm = pm;
        this.modelOutputDestination = modelOutputDestination;
    }
    public void computeAction(){
        String outputPaths = "";
        for (DataSource output : mp.getOutputs()) {
            Path path = Paths.get(modelOutputDestination + output.getName());
            outputPaths += output.getPaths()[0] + ","; // this will leave an extra comma at the end
            byte[] data;
            try {
                data = Files.readAllBytes(path);
                pm.putFile(data, output,0);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        // now print the paths
        if(outputPaths.length() > 0) { //if there were outputs
            outputPaths = outputPaths.substring(0, outputPaths.length()-1); // truncate the extra comma at the end
            String[] pathsArray = outputPaths.split(",");

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String[]> output = new HashMap<String, String[]>();
            output.put("message", pathsArray); // the id for the output of the hms-runner process is "message"

            try {
                String messageJson = objectMapper.writeValueAsString(output);
                String jsonOutput = "{" + "\"plugin_results\":" + messageJson + "}"; // put into json format needed for process-api
                System.out.println(jsonOutput);
            } catch(JsonProcessingException e) {
                e.printStackTrace();
            }
        } else { //if theres no outputs, print empty output
            String jsonOutput = "{\"plugin_results\":{\"message\":[]}}"; // put into format needed for process-api
            System.out.println(jsonOutput);
        }
    }
}
