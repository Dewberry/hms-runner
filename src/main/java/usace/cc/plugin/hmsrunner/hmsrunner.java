package usace.cc.plugin.hmsrunner;

import usace.cc.plugin.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;
import hms.model.Project;
import hms.model.project.ComputeSpecification;
import hms.Hms;
import com.fasterxml.jackson.databind.ObjectMapper;

public class hmsrunner  {
    public static final String PLUGINNAME = "hmsrunner";
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        System.out.println(PLUGINNAME + " says hello!");
        PluginManager pm;
        String jobID = ""; //the ID of the job that is running this hms-runner, passed in the json cmd arg, leave empty if N/A
        if(args.length == 1) {
            Map<String, String> argsMap = new HashMap<String, String>();
            ObjectMapper mapper = new ObjectMapper();
            try {
                argsMap = mapper.readValue(args[0], HashMap.class);
                if(!argsMap.containsKey("root") || !argsMap.containsKey("manifestID")){
                    System.err.println("ERROR: Command line parameter JSON must include `root` and `manifestID` fields");
                    System.exit(1);
                }
                if(!argsMap.containsKey("jobID")){
                    System.err.println("ERROR: Command line parameter JSON must include `jobID` field");
                    System.exit(1);
                }
                pm = PluginManager.getInstance(argsMap.get("root"), argsMap.get("manifestID"));
                jobID = argsMap.get("jobID");
            } catch (Exception e) {
                System.err.println("ERROR: Command line parameter must be a JSON formatted string");
                System.exit(1);
                pm = PluginManager.getInstance();
            }
        } else if(args.length > 1) {
            System.err.println("ERROR: Invalid command-line parameters. Usage: Requires 1 JSON string argument or 0 arguments");
            System.exit(1);
            pm = PluginManager.getInstance();
        } else {
            if(System.getenv("CC_ROOT") == null || System.getenv("CC_MANIFEST_ID") == null) {
                System.err.println("ERROR: root and manifestID must be specified either as command line args or environmental variables");
                System.exit(1);
            }
            pm = PluginManager.getInstance();
        }

        //load payload.
        Payload mp = pm.getPayload();
        //get Alternative name
        String modelName = (String) mp.getAttributes().get("model_name");
        //get simulation name?
        String simulationName = (String) mp.getAttributes().get("simulation");
        //get variant if it exists
        String variantName = (String) mp.getAttributes().get("variant");
        //copy the model to local if not local
        //hard coded outputdestination is fine in a container
        String modelOutputDestination = "/model/"+modelName+"/";
        File dest = new File(modelOutputDestination);
        deleteDirectory(dest);
        //download the payload to list all input files
        String hmsFilePath = "";

        //perform all actions
        for (Action a : mp.getActions()){
            pm.LogMessage(new Message(a.getDescription()));
            switch(a.getName()){
                case "download_inputs":
                    downloadInputsAction dia = new downloadInputsAction(a, mp, pm, modelOutputDestination);
                    dia.computeAction();
                    hmsFilePath = dia.getHMSFilePath();
                    break;
                case "push_outputs":
                    pushOutputsAction poa = new pushOutputsAction(a, mp, pm, modelOutputDestination, jobID);
                    poa.computeAction();
                    break;
                case "compute_forecast":
                    computeForecastAction cfa = new computeForecastAction(a, simulationName, variantName);
                    cfa.computeAction();
                    break;
                case "compute_simulation":
                    computeSimulationAction csa = new computeSimulationAction(a, simulationName);
                    csa.computeAction();
                    break;
                case "dss_to_hdf":
                    dsstoHdfAction da = new dsstoHdfAction(a);
                    da.computeAction();
                    break;
                case "copy_precip_table":
                    copyPrecipAction ca = new copyPrecipAction(a);
                    ca.computeAction();
                    break;
                case "export_excess_precip":
                    Project project = Project.open(hmsFilePath);
                    ComputeSpecification spec = project.getComputeSpecification(simulationName);//move to export precip action eventually
                    exportExcessPrecipAction ea = new exportExcessPrecipAction(a, spec);
                    ea.computeAction();
                    project.close();
                    break;
                case "dss_to_csv":
                    dsstoCsvAction dca = new dsstoCsvAction(a);
                    dca.computeAction();
                    break;
                default:
                break;
            }

        }

        Hms.shutdownEngine();
    }
    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}