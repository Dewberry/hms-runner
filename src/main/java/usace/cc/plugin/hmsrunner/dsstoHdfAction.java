package usace.cc.plugin.hmsrunner;

import hdf.hdf5lib.exceptions.HDF5LibraryException;
import hec.heclib.dss.DSSErrorMessage;
import hec.heclib.dss.HecTimeSeries;
import hec.io.TimeSeriesContainer;
import usace.cc.plugin.Action;
import usace.cc.plugin.DataSource;
import usace.cc.plugin.Payload;
import usace.cc.plugin.PluginManager;

public class dsstoHdfAction {
    private Action action;
    public dsstoHdfAction(Action a) {
        action = a;
    }
    public void computeAction(){
        PluginManager pm = PluginManager.getInstance();
        Payload payload = pm.getPayload();
        //find source 
        DataSource source = action.getParameters().get("source");
        //create dss reader
        //open up the dss file. reference: https://www.hec.usace.army.mil/confluence/display/dssJavaprogrammer/General+Example
        HecTimeSeries reader = new HecTimeSeries();
        int status = reader.setDSSFileName(source.getPaths()[0]);//assumes one path and assumes it is dss.
        if (status <0){
            //panic?
            DSSErrorMessage error = reader.getLastError();
            error.printMessage();
            return;
        }
        //find destination parameter
        DataSource destination = action.getParameters().get("destination");
        //create hdf writer
        h5Connection writer = new h5Connection(destination.getPaths()[0]);//assumes one path and assumes it is hdf.
        try {
            writer.open();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        //read time series from source
        int datasetPathIndex = 0;
        for(String p : source.getDataPaths()){//assumes datapaths for source and dest are ordered the same.
            boolean hasMultiplier = payload.getAttributes().containsKey(p + "- multiplier");
            float multiplier = 1.0f;
            if (hasMultiplier){
                float mult = Float.parseFloat((String) payload.getAttributes().get(p + " - multiplier"));
                multiplier = mult;
            }
            TimeSeriesContainer tsc = new TimeSeriesContainer();
            tsc.fullName = p;

            status = reader.read(tsc,true);
            if (status <0){
                //panic?
                DSSErrorMessage error = reader.getLastError();
                error.printMessage();
                break;
            }
            double[] values = tsc.values;
            double[] times = new double[values.length];
            double delta = 1.0/24.0;//test with other datasets - probably need to make it dependent on d part.
            double timestep = 0;
            int i = 0;
            for(double f : values){
                values[i] = f*multiplier;
                times[i] = timestep;
                timestep += delta;
                i++;
            }
            //write time series to destination
            try {
                writer.write(values,times,destination.getDataPaths()[datasetPathIndex]);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
            datasetPathIndex++;
                                        
        }
        //close reader
        reader.close();
        //close writer
        try {
            writer.close();
        } catch (HDF5LibraryException e) {
            e.printStackTrace();
        }
    }
}
