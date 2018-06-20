package org.fog.placement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.utils.Logger;

public class ModulePlacementEdgewards extends ModulePlacement{
	
	protected ModuleMapping moduleMapping;
	protected List<Sensor> sensors;
	protected List<Actuator> actuators;
	protected Map<Integer, Double> currentCpuLoad;
	
    protected HashMap<String, HashMap<String, Double>> mapModRateValues = new HashMap<String, HashMap<String,Double>>();  //Calculated value for the request rate that arrives to each module from each single (1.0) request to each incoming Tuple from a sensor

	
	/**
	 * Stores the current mapping of application modules to fog devices 
	 */
	protected Map<Integer, List<String>> currentModuleMap;
	protected Map<Integer, Map<String, Double>> currentModuleLoadMap;
	protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum;
	
	public int numOfMigrations = 0;
	public int numOfCloudPlacements = 0;
	
	public ModulePlacementEdgewards(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators, 
			Application application, ModuleMapping moduleMapping,
            Integer[] subAppsRate,
            String resultsFN){
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		setSensors(sensors);
		setActuators(actuators);
		setCurrentCpuLoad(new HashMap<Integer, Double>());
		setCurrentModuleMap(new HashMap<Integer, List<String>>());
		setCurrentModuleLoadMap(new HashMap<Integer, Map<String, Double>>());
		setCurrentModuleInstanceNum(new HashMap<Integer, Map<String, Integer>>());
		for(FogDevice dev : getFogDevices()){
			getCurrentCpuLoad().put(dev.getId(), 0.0);
			getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
			getCurrentModuleMap().put(dev.getId(), new ArrayList<String>());
			getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
		}
		
		mapModules();
		
		calculateModRate();
		calculate_hop_count();
		
		setModuleInstanceCountMap(getCurrentModuleInstanceNum());
		
       System.out.println("NUMERO de migrations:"+numOfMigrations);
       System.out.println("NUMERO de cloud placements:"+numOfCloudPlacements);
       
       
       
/*       Integer maxLevel=0;
       for(FogDevice dev : getFogDevices()){
	   		maxLevel = Math.max(dev.getLevel(),maxLevel);
       }
  */     
/*       currentCpuLoad = getCurrentCpuLoad();
       for(FogDevice dev : getFogDevices()){
    	   		System.out.println("Moduleload:"+currentCpuLoad.get(dev.getId()));
    	   		System.out.println(maxLevel-dev.getLevel());
       }
 */   
/*       for(FogDevice dev : getFogDevices()){
	   		List<String> allocatedModules = currentModuleMap.get(dev.getId());
	   		for (String moduleAlloc : allocatedModules) {
	   			Integer appId = Integer.parseInt(moduleAlloc.substring(moduleAlloc.length()-1));
	   		System.out.println("Module string:"+subAppsRate[appId]);
	   		System.out.println(maxLevel-dev.getLevel());
	   		}
  }       
  */
       
       try {       

	       File archivoCPU = new File("./CPUedge"+resultsFN+".csv");
	       File archivoRAT = new File("./RATedge"+resultsFN+".csv");
	       BufferedWriter bwCPU;
	       BufferedWriter bwRAT;
	       
	
	       bwCPU = new BufferedWriter(new FileWriter(archivoCPU));
		
	       bwRAT = new BufferedWriter(new FileWriter(archivoRAT));
	       
	       bwCPU.write("hopcount;cpuusage;cputotal;numservices\n");
	       bwRAT.write("hopcount;requestratio\n");
	
	
	       
	       
	       
	       
	       
	       Integer maxLevel=0;
	       for(FogDevice dev : getFogDevices()){
		   		maxLevel = Math.max(dev.getLevel(),maxLevel);
	       }
	       
	       for(FogDevice dev : getFogDevices()){
	    	   		Integer hopcountOF = maxLevel-dev.getLevel();
	    	   		Double cpuusageOF = currentCpuLoad.get(dev.getId());
	    	   		Integer cputotalOF = dev.getHost().getTotalMips();
	    	   		Integer numservicesOF = currentModuleMap.get(dev.getId()).size();
	    	   		if ( hopcountOF > 0 ) {
	    	   			bwCPU.write(hopcountOF+";"+cpuusageOF+";"+cputotalOF+";"+numservicesOF+"\n");
	    	   		}
	    	   		
	    	   		
	       }
	       
	       for(FogDevice dev : getFogDevices()){
	    	   		List<String> allocatedModules = currentModuleMap.get(dev.getId());
	    	   		for (String moduleAlloc : allocatedModules) {
	    	   			Integer appId = Integer.parseInt(moduleAlloc.substring(moduleAlloc.length()-1));

		    	   		
			   		Integer hopcountOF = maxLevel-dev.getLevel();
		    	   		Integer requestratioOF = subAppsRate[appId];
		    	   		if ( hopcountOF > 0 ) {
		    	   			bwRAT.write(hopcountOF+";"+requestratioOF+"\n");		
		    	   		}
			   		
			   		
	    	   		}
	       }
	       
	       bwCPU.close();
	       bwRAT.close();
       
       } catch (IOException e) {
   		// TODO Auto-generated catch block
   		e.printStackTrace();
   	}       
		
	}
	
	

    protected void calculateModRate() {
    	

        
        for (AppModule module : getApplication().getModules()) {
	        	for (AppEdge edge : getApplication().getEdges()) {
	    			if (edge.getDestination().equals(module.getName())
	    				&& edge.getEdgeType()==AppEdge.SENSOR ){
		        		double currentRate = 1.0;
		  
		        		String initialTupleType = edge.getTupleType();
		        		
		        		HashMap<String, Double> mapTargetModule = null;
		        		if ( ( mapTargetModule = mapModRateValues.get(module.getName())) != null ) {
		        			Double finalvalue;
		        			if ( (finalvalue = mapTargetModule.get(initialTupleType)) != null ){
		        				finalvalue += currentRate;
		        				mapTargetModule.put(initialTupleType, finalvalue);
		        			} else {
		        				mapTargetModule.put(initialTupleType, currentRate);
		        			}
		        			
		        			
		        		}else {
		        			
		        			mapTargetModule = new HashMap<String, Double>();
		        			mapTargetModule.put(initialTupleType, currentRate);
		        			mapModRateValues.put(module.getName(), mapTargetModule);
		        		}
		        		
		        		
		        		pathModRate(module,module,currentRate,edge.getTupleType(),initialTupleType);
	    			}
	    			
	        	}
        }
  }
        
	
	
	
	
	
	
	
	   protected void pathModRate(AppModule pathModuleInput, AppModule currentModule, double currentRate, String incomingTupleType, String initialTupleType) {
   		for (AppEdge edge : getApplication().getEdges()) {
   			if (edge.getSource().equals(currentModule.getName())
   				&& edge.getEdgeType()!=AppEdge.ACTUATOR ){
 
//   				System.out.println("Current:"+currentModule.getName());
//   				System.out.print(edge.getTupleType()+"::::");
//   				System.out.print(edge.getSource());
//   				System.out.print("====>");
//   				System.out.println(edge.getDestination());
   				AppModule destinationModule = getApplication().getModuleByName(edge.getDestination());
   				String outgoingTupleType = edge.getTupleType();
   				SelectivityModel s = currentModule.getSelectivityMap().get(new Pair<String, String>(incomingTupleType, outgoingTupleType));
	    			if (s!=null) {
//	    				System.out.print(pathModuleInput.getName());
//	    				System.out.print("====>");
//	    				System.out.println(destinationModule.getName());
	    				
	    				double newRate = currentRate * s.getMeanRate();
	    				
	    			
		        		HashMap<String, Double> mapTargetModule = null;
		        		if ( ( mapTargetModule = mapModRateValues.get(destinationModule.getName())) != null ) {
		        			Double finalvalue;
		        			if ( (finalvalue = mapTargetModule.get(initialTupleType)) != null ){
		        				finalvalue += newRate;
		        				mapTargetModule.put(initialTupleType, finalvalue);
		        			} else {
		        				mapTargetModule.put(initialTupleType, newRate);
		        			}
		        			
		        			
		        		}else {
		        			
		        			mapTargetModule = new HashMap<String, Double>();
		        			mapTargetModule.put(initialTupleType, newRate);
		        			mapModRateValues.put(destinationModule.getName(), mapTargetModule);
		        		}
	    				
	    				
	    				
	    				
	    				
		        		pathModRate(pathModuleInput,destinationModule,newRate,outgoingTupleType,initialTupleType);
		    				    			}
   			}
   }
  
  	
   	
   	
   	
   }
	
	
	
	   protected void calculate_hop_count() {
	       
	        double weighted_hop_count = 0.0;
	        double weighted_ratio = 0.0;
	        double hop_count = 0.0;
	        double num_evaluations = 0.0;
	    	
	    	
	       for (Sensor sensor : sensors) {
	    	   		for (AppModule mod : getApplication().getModules()) {
	    	   			Double ratioMod = mapModRateValues.get(mod.getName()).get(sensor.getTupleType());
	    	   			if (ratioMod!=null) {
	        	   			Double ratioSen = 1.0/sensor.getTransmitDistribution().getMeanInterTransmitTime();
	        	   			Double ratio = ratioMod * ratioSen;
//	    	   				System.out.println("tupla:"+sensor.getTupleType());
//	    	   				System.out.println("mod:"+mod.getName());
//	    	   				System.out.println("ratemod:"+mapModRateValues.get(mod.getName()).get(sensor.getTupleType()));;
//	        	   			System.out.println("ratetot:"+ratio);
	    	   				FogDevice mobile =getFogDeviceById(sensor.getGatewayDeviceId());
	    	   				num_evaluations ++;

	    	   				
	    	   				double hc = hopCount(mobile,mod.getName()); //the mobile to which the sensor is connected it is not considered
//	        	   			System.out.println("hc:"+hc);
	        	   			
//	        	   			System.out.println((double)((double)ratio*(double)hc));
	    	   				hop_count += hc;
	        	   			weighted_hop_count += (double)((double)ratio*(double)hc);
	        	   			weighted_ratio += ratio;
	    	   			}
		    	   		 
		    	   		
	    	   		}
	       }
	       
//	       System.out.println("t"+weighted_hop_count);
//	       System.out.println("t"+weighted_ratio);
	       weighted_hop_count = (double)((double)weighted_hop_count/ (double)weighted_ratio);
	       hop_count = hop_count/num_evaluations;
	       System.out.println("Total average weighted hop count: "+weighted_hop_count);
	       System.out.println("Total average hop count: "+hop_count);
	        
	    }
	    
//	    protected void removePreAllocate(FogDevice dev, String mod) {
//	    		List<String> currentModules = currentModuleMap.get(dev);
//	    		currentModules.remove(mod);
//	    }
	    
	    
	    protected int hopCount(FogDevice dev, String modName) {
	    	
	    		if (dev.getLevel()==0) {
	    			return 0;
	    		}
	    		if (currentModuleMap.get(dev.getId()).contains(modName)) {
	    			return 0;
	    		}
	    	
	    		return 1+hopCount(getFogDeviceById(dev.getParentId()),modName);
	    	
	    }
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	@Override
	protected void mapModules() {
		
		for(String deviceName : getModuleMapping().getModuleMapping().keySet()){
			for(String moduleName : getModuleMapping().getModuleMapping().get(deviceName)){
				int deviceId = CloudSim.getEntityId(deviceName);
				getCurrentModuleMap().get(deviceId).add(moduleName);
				getCurrentModuleLoadMap().get(deviceId).put(moduleName, 0.0);
				getCurrentModuleInstanceNum().get(deviceId).put(moduleName, 0);
			}
		}
		
		List<List<Integer>> leafToRootPaths = getLeafToRootPaths();
		
		for(List<Integer> path : leafToRootPaths){
			placeModulesInPath(path);
		}
		
		for(int deviceId : getCurrentModuleMap().keySet()){
			for(String module : getCurrentModuleMap().get(deviceId)){
				createModuleInstanceOnDevice(getApplication().getModuleByName(module), getFogDeviceById(deviceId));
			}
		}
	}
	
	/**
	 * Get the list of modules that are ready to be placed 
	 * @param placedModules Modules that have already been placed in current path
	 * @return list of modules ready to be placed
	 */
	private List<String> getModulesToPlace(List<String> placedModules){
		Application app = getApplication();
		List<String> modulesToPlace_1 = new ArrayList<String>();
		List<String> modulesToPlace = new ArrayList<String>();
		for(AppModule module : app.getModules()){
			if(!placedModules.contains(module.getName()))
				modulesToPlace_1.add(module.getName());
		}
		/*
		 * Filtering based on whether modules (to be placed) lower in physical topology are already placed
		 */
		for(String moduleName : modulesToPlace_1){
			boolean toBePlaced = true;
			
			for(AppEdge edge : app.getEdges()){
				//CHECK IF OUTGOING DOWN EDGES ARE PLACED
				if(edge.getSource().equals(moduleName) && edge.getDirection()==Tuple.DOWN && !placedModules.contains(edge.getDestination()))
					toBePlaced = false;
				//CHECK IF INCOMING UP EDGES ARE PLACED
				if(edge.getDestination().equals(moduleName) && edge.getDirection()==Tuple.UP && !placedModules.contains(edge.getSource()))
					toBePlaced = false;
			}
			if(toBePlaced)
				modulesToPlace.add(moduleName);
		}

		return modulesToPlace;
	}
	
	protected double getRateOfSensor(String sensorType){
		for(Sensor sensor : getSensors()){
			if(sensor.getTupleType().equals(sensorType))
				return 1/sensor.getTransmitDistribution().getMeanInterTransmitTime();
		}
		return 0;
	}
	
	private void placeModulesInPath(List<Integer> path) {
		if(path.size()==0)return;
		List<String> placedModules = new ArrayList<String>();
		Map<AppEdge, Double> appEdgeToRate = new HashMap<AppEdge, Double>();
		
		/**
		 * Periodic edges have a fixed periodicity of tuples, so setting the tuple rate beforehand
		 */
		for(AppEdge edge : getApplication().getEdges()){
			if(edge.isPeriodic()){
				appEdgeToRate.put(edge, 1/edge.getPeriodicity());
			}
		}
		
		for(Integer deviceId : path){
			FogDevice device = getFogDeviceById(deviceId);
			Map<String, Integer> sensorsAssociated = getAssociatedSensors(device);
			Map<String, Integer> actuatorsAssociated = getAssociatedActuators(device);
			placedModules.addAll(sensorsAssociated.keySet()); // ADDING ALL SENSORS TO PLACED LIST
			placedModules.addAll(actuatorsAssociated.keySet()); // ADDING ALL ACTUATORS TO PLACED LIST
			
			/*
			 * Setting the rates of application edges emanating from sensors
			 */
			for(String sensor : sensorsAssociated.keySet()){
				for(AppEdge edge : getApplication().getEdges()){
					if(edge.getSource().equals(sensor)){
						appEdgeToRate.put(edge, sensorsAssociated.get(sensor)*getRateOfSensor(sensor));
					}
				}
			}
						
			/*
			 * Updating the AppEdge rates for the entire application based on knowledge so far
			 */
			boolean changed = true;
			while(changed){		//Loop runs as long as some new information is added
				changed=false;
				Map<AppEdge, Double> rateMap = new HashMap<AppEdge, Double>(appEdgeToRate);
				for(AppEdge edge : rateMap.keySet()){
					AppModule destModule = getApplication().getModuleByName(edge.getDestination());
					if(destModule == null)continue;
					Map<Pair<String, String>, SelectivityModel> map = destModule.getSelectivityMap();
					for(Pair<String, String> pair : map.keySet()){
						if(pair.getFirst().equals(edge.getTupleType())){
							double outputRate = appEdgeToRate.get(edge)*map.get(pair).getMeanRate(); // getting mean rate from SelectivityModel
							AppEdge outputEdge = getApplication().getEdgeMap().get(pair.getSecond());
							if(!appEdgeToRate.containsKey(outputEdge) || appEdgeToRate.get(outputEdge)!=outputRate){
								// if some new information is available
								changed = true;
							}
							appEdgeToRate.put(outputEdge, outputRate);
						}
					}
				}
			}
			
			/*
			 * Getting the list of modules ready to be placed on current device on path
			 */
			List<String> modulesToPlace = getModulesToPlace(placedModules);
			
			while(modulesToPlace.size() > 0){ // Loop runs until all modules in modulesToPlace are deployed in the path
				String moduleName = modulesToPlace.get(0);
				double totalCpuLoad = 0;
				
				//IF MODULE IS ALREADY PLACED UPSTREAM, THEN UPDATE THE EXISTING MODULE
				int upsteamDeviceId = isPlacedUpstream(moduleName, path);
				if(upsteamDeviceId > 0){
					if(upsteamDeviceId==deviceId){
						placedModules.add(moduleName);
						modulesToPlace = getModulesToPlace(placedModules);
						
						// NOW THE MODULE TO PLACE IS IN THE CURRENT DEVICE. CHECK IF THE NODE CAN SUSTAIN THE MODULE
						for(AppEdge edge : getApplication().getEdges()){		// take all incoming edges
							if(edge.getDestination().equals(moduleName)){
								double rate = appEdgeToRate.get(edge);
								totalCpuLoad += rate*edge.getTupleCpuLength();
							}
						}
						if(totalCpuLoad + getCurrentCpuLoad().get(deviceId) > device.getHost().getTotalMips()){
							Logger.debug("ModulePlacementEdgeward", "Need to shift module "+moduleName+" upstream from device " + device.getName());
							List<String> _placedOperators = shiftModuleNorth(moduleName, totalCpuLoad, deviceId, modulesToPlace);
							for(String placedOperator : _placedOperators){
								if(!placedModules.contains(placedOperator))
									placedModules.add(placedOperator);
							}
						} else{
							placedModules.add(moduleName);
							getCurrentCpuLoad().put(deviceId, getCurrentCpuLoad().get(deviceId)+totalCpuLoad);
							getCurrentModuleInstanceNum().get(deviceId).put(moduleName, getCurrentModuleInstanceNum().get(deviceId).get(moduleName)+1);
							Logger.debug("ModulePlacementEdgeward", "AppModule "+moduleName+" can be created on device "+device.getName());
						}
					}
				}else{
					// FINDING OUT WHETHER PLACEMENT OF OPERATOR ON DEVICE IS POSSIBLE
					for(AppEdge edge : getApplication().getEdges()){		// take all incoming edges
						if(edge.getDestination().equals(moduleName)){
							double rate = appEdgeToRate.get(edge);
							totalCpuLoad += rate*edge.getTupleCpuLength();
						}
					}
						
					if(totalCpuLoad + getCurrentCpuLoad().get(deviceId) > device.getHost().getTotalMips()){
						Logger.debug("ModulePlacementEdgeward", "Placement of operator "+moduleName+ "NOT POSSIBLE on device "+device.getName());
						numOfMigrations++;
					}
					else{
						Logger.debug("ModulePlacementEdgeward", "Placement of operator "+moduleName+ " on device "+device.getName() + " successful.");
						getCurrentCpuLoad().put(deviceId, totalCpuLoad + getCurrentCpuLoad().get(deviceId));
//						System.out.println("Placement of operator "+moduleName+ " on device "+device.getName() + " successful.");

						if(!currentModuleMap.containsKey(deviceId))
							currentModuleMap.put(deviceId, new ArrayList<String>());
						currentModuleMap.get(deviceId).add(moduleName);
						placedModules.add(moduleName);
						modulesToPlace = getModulesToPlace(placedModules);
						getCurrentModuleLoadMap().get(device.getId()).put(moduleName, totalCpuLoad);
						
						int max = 1;
						for(AppEdge edge : getApplication().getEdges()){
							if(edge.getSource().equals(moduleName) && actuatorsAssociated.containsKey(edge.getDestination()))
								max = Math.max(actuatorsAssociated.get(edge.getDestination()), max);
							if(edge.getDestination().equals(moduleName) && sensorsAssociated.containsKey(edge.getSource()))
								max = Math.max(sensorsAssociated.get(edge.getSource()), max);
						}
						getCurrentModuleInstanceNum().get(deviceId).put(moduleName, max);
					}
				}
			
			
				modulesToPlace.remove(moduleName);
			}
			
		}
		
	}

	/**
	 * Shifts a module moduleName from device deviceId northwards. This involves other modules that depend on it to be shifted north as well.
	 * @param moduleName
	 * @param cpuLoad cpuLoad of the module
	 * @param deviceId
	 */
	private List<String> shiftModuleNorth(String moduleName, double cpuLoad, Integer deviceId, List<String> operatorsToPlace) {
//		System.out.println(CloudSim.getEntityName(deviceId)+" is shifting "+moduleName+" north.");
		List<String> modulesToShift = findModulesToShift(moduleName, deviceId);
		
		Map<String, Integer> moduleToNumInstances = new HashMap<String, Integer>(); // Map of number of instances of modules that need to be shifted
		double totalCpuLoad = 0;
		Map<String, Double> loadMap = new HashMap<String, Double>();
		for(String module : modulesToShift){
			loadMap.put(module, getCurrentModuleLoadMap().get(deviceId).get(module));
			moduleToNumInstances.put(module, getCurrentModuleInstanceNum().get(deviceId).get(module)+1);
			totalCpuLoad += getCurrentModuleLoadMap().get(deviceId).get(module);
			getCurrentModuleLoadMap().get(deviceId).remove(module);
			getCurrentModuleMap().get(deviceId).remove(module);
			getCurrentModuleInstanceNum().get(deviceId).remove(module);
			numOfMigrations++;
		}
		
		getCurrentCpuLoad().put(deviceId, getCurrentCpuLoad().get(deviceId)-totalCpuLoad); // change info of current CPU load on device
		loadMap.put(moduleName, loadMap.get(moduleName)+cpuLoad);
		totalCpuLoad += cpuLoad;
		
		int id = getParentDevice(deviceId);
		while(true){ // Loop iterates over all devices in path upstream from current device. Tries to place modules (to be shifted northwards) on each of them.
			if(id==-1){
				// Loop has reached the apex fog device in hierarchy, and still could not place modules. 
				Logger.debug("ModulePlacementEdgeward", "Could not place modules "+modulesToShift+" northwards.");
				//numOfCloudPlacements++;
				break;
			}
			FogDevice fogDevice = getFogDeviceById(id);
			if (fogDevice.getLevel()==0) {
				numOfCloudPlacements++;
			}
			if(getCurrentCpuLoad().get(id) + totalCpuLoad > fogDevice.getHost().getTotalMips()){
				// Device cannot take up CPU load of incoming modules. Keep searching for device further north.
				List<String> _modulesToShift = findModulesToShift(modulesToShift, id);	// All modules in _modulesToShift are currently placed on device id
				double cpuLoadShifted = 0;		// the total CPU load shifted from device id to its parent
				for(String module : _modulesToShift){
					if(!modulesToShift.contains(module)){
						// Add information of all newly added modules (to be shifted) 
						moduleToNumInstances.put(module, getCurrentModuleInstanceNum().get(id).get(module)+moduleToNumInstances.get(module));
						loadMap.put(module, getCurrentModuleLoadMap().get(id).get(module));
						cpuLoadShifted += getCurrentModuleLoadMap().get(id).get(module);
						totalCpuLoad += getCurrentModuleLoadMap().get(id).get(module);
						// Removing information of all modules (to be shifted north) in device with ID id 
						getCurrentModuleLoadMap().get(id).remove(module);
						getCurrentModuleMap().get(id).remove(module);
						getCurrentModuleInstanceNum().get(id).remove(module);
					}					
				}
				getCurrentCpuLoad().put(id, getCurrentCpuLoad().get(id)-cpuLoadShifted); // CPU load on device id gets reduced due to modules shifting northwards
				
				modulesToShift = _modulesToShift;
				id = getParentDevice(id); // iterating to parent device
			} else{
				// Device (@ id) can accommodate modules. Placing them here.
				double totalLoad = 0;
				for(String module : loadMap.keySet()){
					totalLoad += loadMap.get(module);
					getCurrentModuleLoadMap().get(id).put(module, loadMap.get(module));
					getCurrentModuleMap().get(id).add(module);
					String module_ = module;
					int initialNumInstances = 0;
					if(getCurrentModuleInstanceNum().get(id).containsKey(module_))
						initialNumInstances = getCurrentModuleInstanceNum().get(id).get(module_);
					int finalNumInstances = initialNumInstances + moduleToNumInstances.get(module_);
					getCurrentModuleInstanceNum().get(id).put(module_, finalNumInstances);
				}
				getCurrentCpuLoad().put(id, totalLoad);
				operatorsToPlace.removeAll(loadMap.keySet());
				List<String> placedOperators = new ArrayList<String>();
				for(String op : loadMap.keySet())placedOperators.add(op);
				return placedOperators;
			}	
		}
		return new ArrayList<String>();
	}

	/**
	 * Get all modules that need to be shifted northwards along with <b>module</b>.  
	 * Typically, these other modules are those that are hosted on device with ID <b>deviceId</b> and lie upstream of <b>module</b> in application model. 
	 * @param module the module that needs to be shifted northwards
	 * @param deviceId the fog device ID that it is currently on
	 * @return list of all modules that need to be shifted north along with <b>module</b>
	 */
	private List<String> findModulesToShift(String module, Integer deviceId) {
		List<String> modules = new ArrayList<String>();
		modules.add(module);
		return findModulesToShift(modules, deviceId);
		/*List<String> upstreamModules = new ArrayList<String>();
		upstreamModules.add(module);
		boolean changed = true;
		while(changed){ // Keep loop running as long as new information is added.
			changed = false;
			for(AppEdge edge : getApplication().getEdges()){
				
				 * If there is an application edge UP from the module to be shifted to another module in the same device
				 
				if(upstreamModules.contains(edge.getSource()) && edge.getDirection()==Tuple.UP && 
						getCurrentModuleMap().get(deviceId).contains(edge.getDestination()) 
						&& !upstreamModules.contains(edge.getDestination())){
					upstreamModules.add(edge.getDestination());
					changed = true;
				}
			}
		}
		return upstreamModules;	*/
	}
	/**
	 * Get all modules that need to be shifted northwards along with <b>modules</b>.  
	 * Typically, these other modules are those that are hosted on device with ID <b>deviceId</b> and lie upstream of modules in <b>modules</b> in application model. 
	 * @param module the module that needs to be shifted northwards
	 * @param deviceId the fog device ID that it is currently on
	 * @return list of all modules that need to be shifted north along with <b>modules</b>
	 */
	private List<String> findModulesToShift(List<String> modules, Integer deviceId) {
		List<String> upstreamModules = new ArrayList<String>();
		upstreamModules.addAll(modules);
		boolean changed = true;
		while(changed){ // Keep loop running as long as new information is added.
			changed = false;
			/*
			 * If there is an application edge UP from the module to be shifted to another module in the same device
			 */
			for(AppEdge edge : getApplication().getEdges()){
				if(upstreamModules.contains(edge.getSource()) && edge.getDirection()==Tuple.UP && 
						getCurrentModuleMap().get(deviceId).contains(edge.getDestination()) 
						&& !upstreamModules.contains(edge.getDestination())){
					upstreamModules.add(edge.getDestination());
					changed = true;
				}
			}
		}
		return upstreamModules;	
	}
	
	private int isPlacedUpstream(String operatorName, List<Integer> path) {
		for(int deviceId : path){
			if(currentModuleMap.containsKey(deviceId) && currentModuleMap.get(deviceId).contains(operatorName))
				return deviceId;
		}
		return -1;
	}

	/**
	 * Gets all sensors associated with fog-device <b>device</b>
	 * @param device
	 * @return map from sensor type to number of such sensors
	 */
	private Map<String, Integer> getAssociatedSensors(FogDevice device) {
		Map<String, Integer> endpoints = new HashMap<String, Integer>();
		for(Sensor sensor : getSensors()){
			if(sensor.getGatewayDeviceId()==device.getId()){
				if(!endpoints.containsKey(sensor.getTupleType()))
					endpoints.put(sensor.getTupleType(), 0);
				endpoints.put(sensor.getTupleType(), endpoints.get(sensor.getTupleType())+1);
			}
		}
		return endpoints;
	}
	
	/**
	 * Gets all actuators associated with fog-device <b>device</b>
	 * @param device
	 * @return map from actuator type to number of such sensors
	 */
	private Map<String, Integer> getAssociatedActuators(FogDevice device) {
		Map<String, Integer> endpoints = new HashMap<String, Integer>();
		for(Actuator actuator : getActuators()){
			if(actuator.getGatewayDeviceId()==device.getId()){
				if(!endpoints.containsKey(actuator.getActuatorType()))
					endpoints.put(actuator.getActuatorType(), 0);
				endpoints.put(actuator.getActuatorType(), endpoints.get(actuator.getActuatorType())+1);
			}
		}
		return endpoints;
	}
	
	@SuppressWarnings("serial")
	protected List<List<Integer>> getPaths(final int fogDeviceId){
		FogDevice device = (FogDevice)CloudSim.getEntity(fogDeviceId); 
		if(device.getChildrenIds().size() == 0){		
			final List<Integer> path =  (new ArrayList<Integer>(){{add(fogDeviceId);}});
			List<List<Integer>> paths = (new ArrayList<List<Integer>>(){{add(path);}});
			return paths;
		}
		List<List<Integer>> paths = new ArrayList<List<Integer>>();
		for(int childId : device.getChildrenIds()){
			List<List<Integer>> childPaths = getPaths(childId);
			for(List<Integer> childPath : childPaths)
				childPath.add(fogDeviceId);
			paths.addAll(childPaths);
		}
		return paths;
	}
	
	protected List<List<Integer>> getLeafToRootPaths(){
		FogDevice cloud=null;
		for(FogDevice device : getFogDevices()){
			if(device.getName().equals("cloud"))
				cloud = device;
		}
		return getPaths(cloud.getId());
	}
	
	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, List<String>> getCurrentModuleMap() {
		return currentModuleMap;
	}

	public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
		this.currentModuleMap = currentModuleMap;
	}

	public List<Sensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<Sensor> sensors) {
		this.sensors = sensors;
	}

	public List<Actuator> getActuators() {
		return actuators;
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = actuators;
	}

	public Map<Integer, Double> getCurrentCpuLoad() {
		return currentCpuLoad;
	}

	public void setCurrentCpuLoad(Map<Integer, Double> currentCpuLoad) {
		this.currentCpuLoad= currentCpuLoad;
	}

	public Map<Integer, Map<String, Double>> getCurrentModuleLoadMap() {
		return currentModuleLoadMap;
	}

	public void setCurrentModuleLoadMap(
			Map<Integer, Map<String, Double>> currentModuleLoadMap) {
		this.currentModuleLoadMap = currentModuleLoadMap;
	}

	public Map<Integer, Map<String, Integer>> getCurrentModuleInstanceNum() {
		return currentModuleInstanceNum;
	}

	public void setCurrentModuleInstanceNum(
			Map<Integer, Map<String, Integer>> currentModuleInstanceNum) {
		this.currentModuleInstanceNum = currentModuleInstanceNum;
	}
}
