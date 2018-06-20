/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.fog.placement;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

/**
 *
 * @author judit
 * 
 * 
 * RESTRICCIONES
 * 
 * -No hay tuples periódicas
 * -Solo puede llegar sensores al module Source
 * -Solo cojo los modules SOURCE que al menos le llega un sensor
 * -Solo puede haber un TUPLE que llegue como tipo SENSOR al module Source
 * - La estructura de los modulos ha de ser un arbol con igual profundidad para todos los caminos.
 * 
 * 
 * 
 */
public class ModulePlacementPopularity extends ModulePlacement {

   // HashMap<AppModule, FogDevice> modulesToPlace;
//    HashMap<AppModule, Double> moduleEdgeRate;

//    Map<AppEdge, Double> appEdgeToRate = new HashMap<AppEdge, Double>();
	
	protected ModuleMapping moduleMapping;
    protected List<Sensor> sensors;  // List of sensors
    protected List<Actuator> actuators;  //List of actuators
    protected HashMap<String, HashMap<String, Double>> mapModRateValues = new HashMap<String, HashMap<String,Double>>();  //Calculated value for the request rate that arrives to each module from each single (1.0) request to each incoming Tuple from a sensor
    protected HashMap<String, HashMap<String, Double>> mapEdgeRateValues = new HashMap<String, HashMap<String,Double>>();  //Calculated value for the request rate that arrives to each Tuple from each single (1.0) request to each app-incoming Tuple from a sensor
    protected HashMap<String, HashMap<String, Double>> mapDeviceSensorRate = new HashMap<String, HashMap<String,Double>>();  //Calculated value for the request rate that arrives to each device adding all the sensor rates that are lower to them 
    protected Set<FogDevice> gateways = new HashSet<FogDevice>();  //List of the leaf devices where the mobile devices are connecte
    protected Map<FogDevice, Double> currentCpuLoad = new HashMap<FogDevice, Double>();  //Load of the cpu of each device in MIPS
    protected List<Pair<FogDevice, String>> modulesToPlace = new ArrayList<Pair<FogDevice,String>>();  // SAR (Service Allocation Requests) that are stil pending to be analyzed and decide to be placed
    
    
	protected Map<FogDevice, List<String>> currentModuleMap = new HashMap<FogDevice, List<String>>();  //Preallocated list of pairs device-module
	protected List<String> moduleOrder = new ArrayList<String>();  //Order of the modules of the app that need to be respected to accomplish the consumption relationships
	protected Map<String, List<String>> mapModuleClosure = new HashMap<String, List<String>>();  //The closure of each element in the app graph
	
	public int numOfMigrations = 0;
	public int numOfCloudPlacements = 0;
    
    

//    protected double[][] rateValues; 
//    protected double[][] deviceSensorRate;
   
	public Integer aaaa() {
		return numOfMigrations;
	}

    public ModulePlacementPopularity(List<FogDevice> fogDevices,
            List<Sensor> sensors,
            List<Actuator> actuators,
            Application application,
            ModuleMapping moduleMapping,
            Integer[] subAppsRate,
            String resultsFN) {

        this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
        this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
        this.sensors = sensors;
        
        this.moduleMapping = moduleMapping;
        
	    	for(FogDevice dev : getFogDevices()){
				currentCpuLoad.put(dev, 0.0);
//				getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
				currentModuleMap.put(dev, new ArrayList<String>());
//				getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
		}


        
        searchGateways();
        calculateEdgeRate();
        calculateModRate();
        calculateDeviceSensorDependencies();
        calculateModuleOrder();
        calculateClosure();

       
       mapModulesPreviouslyMapped(); 
        
       mapModules();
       
       
       calculate_hop_count();
       
       System.out.println("NUMERO de migrations:"+numOfMigrations);
       System.out.println("NUMERO de cloud placements:"+numOfCloudPlacements);
       
       
       try {       

	       File archivoCPU = new File("./CPUpop"+resultsFN+".csv");
	       File archivoRAT = new File("./RATpop"+resultsFN+".csv");
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
	    	   		Double cpuusageOF = currentCpuLoad.get(dev);
	    	   		Integer cputotalOF = dev.getHost().getTotalMips();
	    	   		Integer numservicesOF = currentModuleMap.get(dev).size();
	    	   		if ( hopcountOF > 0 ) {
	    	   			bwCPU.write(hopcountOF+";"+cpuusageOF+";"+cputotalOF+";"+numservicesOF+"\n");
	    	   		}
	    	   		
	    	   		
	    	   		
	       }
	       
	       for(FogDevice dev : getFogDevices()){
	    	   		List<String> allocatedModules = currentModuleMap.get(dev);
	    	   		for (String moduleAlloc : allocatedModules) {
	    	   			Integer appId = Integer.parseInt(moduleAlloc.substring(moduleAlloc.length()-1));
	    	   			double moduleRateOF = calculateModuleRate(dev, moduleAlloc);
		    	   		
			   		Integer hopcountOF = maxLevel-dev.getLevel();
		    	   		Integer requestratioOF = subAppsRate[appId];
		    	   		if ( hopcountOF > 0 ) {
		    	   			bwRAT.write(hopcountOF+";"+requestratioOF+";"+moduleRateOF+"\n");			   		
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
//    	   				System.out.println("tupla:"+sensor.getTupleType());
//    	   				System.out.println("mod:"+mod.getName());
//    	   				System.out.println("ratemod:"+mapModRateValues.get(mod.getName()).get(sensor.getTupleType()));;
//        	   			System.out.println("ratetot:"+ratio);
    	   				FogDevice mobile =getFogDeviceById(sensor.getGatewayDeviceId());
    	   				num_evaluations ++;

    	   				
    	   				double hc = hopCount(mobile,mod.getName()); //the mobile to which the sensor is connected it is not considered
//        	   			System.out.println("hc:"+hc);
        	   			
//        	   			System.out.println((double)((double)ratio*(double)hc));
    	   				hop_count += hc;
        	   			weighted_hop_count += (double)((double)ratio*(double)hc);
        	   			weighted_ratio += ratio;
    	   			}
	    	   		 
	    	   		
    	   		}
       }
       
//       System.out.println("t"+weighted_hop_count);
//       System.out.println("t"+weighted_ratio);
       weighted_hop_count = (double)((double)weighted_hop_count/ (double)weighted_ratio);
       hop_count = hop_count/num_evaluations;
       System.out.println("Total average weighted hop count: "+weighted_hop_count);
       System.out.println("Total average hop count: "+hop_count);
        
    }
    
//    protected void removePreAllocate(FogDevice dev, String mod) {
//    		List<String> currentModules = currentModuleMap.get(dev);
//    		currentModules.remove(mod);
//    }
    
    
    protected int hopCount(FogDevice dev, String modName) {
    	
    		if (dev.getLevel()==0) {
    			return 0;
    		}
    		if (currentModuleMap.get(dev).contains(modName)) {
    			return 0;
    		}
    	
    		return 1+hopCount(getFogDeviceById(dev.getParentId()),modName);
    	
    }
    
    protected void mapModulesPreviouslyMapped() {
    	
		for(String deviceName : moduleMapping.getModuleMapping().keySet()){
			for(String moduleName : moduleMapping.getModuleMapping().get(deviceName)){
				int deviceId = CloudSim.getEntityId(deviceName);
				
				PreAllocate(getFogDeviceById(deviceId),moduleName);

			}
		}
    	
    }
    
    
    protected void PreAllocate(FogDevice dev, String mod) {
		List<String> currentModules;
		if ( (currentModules = currentModuleMap.get(dev)) != null ) {
			currentModules.add(mod);
		}else {
			currentModules = new ArrayList<String>();
			currentModules.add(mod);
			currentModuleMap.put(dev,currentModules);
		}
    }
  
    
    
    protected double calculateResourceUsageEdge(FogDevice dev, AppEdge edge) {
    	
    		double totalUsage = 0.0;
    		
    		HashMap<String, Double> deviceRequestRate = mapDeviceSensorRate.get(dev.getName());
    		HashMap<String, Double> edgeRequestRate = mapEdgeRateValues.get(edge.getTupleType());
    		
    		
    		for (Map.Entry<String, Double> edgeMapEntry : edgeRequestRate.entrySet()) {
    			Double deviceRate;
    			if ( (deviceRate = deviceRequestRate.get(edgeMapEntry.getKey()))!=null ) {
    				totalUsage += deviceRate * edgeMapEntry.getValue() * edge.getTupleCpuLength();
//    				System.out.println("Calculating resource usage for device "+dev.getName()+" (deviceRate="+deviceRate+") and edge "+edge.getTupleType()+" (edgeRequestRate="+edgeMapEntry.getValue()+") with a CPU length of the edge of "+edge.getTupleCpuLength());
    			}
    		}
    		
    	
    		return totalUsage;
    	
    }
    
    
    
    protected double calculateResourceUsage(FogDevice dev, String module) {
    	
    		double totalUsage = 0.0;
    		
//    		System.out.println("***** Calculating resource usage for module "+module);
    		for (AppEdge edge : getApplication().getEdges()) {
    			if (edge.getDestination().equals(module)) {

    				totalUsage += calculateResourceUsageEdge(dev,edge);

    			}
    		}   		
    		
    		
    	
    		return totalUsage;
    }
    
    
    protected double calculateClosureRes(FogDevice dev,List<String> toDealloc) {

    		double totalUsage = 0.0;
    		for (String mod : toDealloc) {
    			totalUsage += calculateResourceUsage(dev, mod);
    		}
    		
    		return totalUsage;
    	
    }
    
    
    
    protected Pair<Double,List<String>> DeallocateLowerRequestedClosure( FogDevice dev, String modName, List<String> candidatesToDeallocate, HashMap<String,Double> allocModRate){
    	
    		
    	
	    	List<String> currentModules = currentModuleMap.get(dev);
	    	Double minRate = Double.MAX_VALUE;
    		List<String> minClosure = new ArrayList<String>();
	    
//    		System.out.println("**currenModules en deallocatedlowerrequestedclosure for dev "+dev.getName());
//    		
//    		for (String ss : currentModules) {
//    			System.out.print(ss + "    ");
//    		}
//    		System.out.println("");
//    		System.out.println("**END de currenModules en deallocatedlowerrequestedclosure for dev "+dev.getName());
    		
    		
	    	
	    	for (String mod : currentModules) {
	    		if (!candidatesToDeallocate.contains(mod)) {
	    			if (!mapModuleClosure.get(mod).contains(modName)) {
			    		List<String> modClosure = mapModuleClosure.get(mod);
			    		List<String> allocModClosure = new ArrayList<String>();
			    		Double tmpRate = 0.0;
			    		int numMod = 0;
			    		for (String s : modClosure) {
			    			if (currentModules.contains(s)) {
			    				allocModClosure.add(s);
			    				
			    				tmpRate += allocModRate.get(s);
			    				numMod ++;
			    			}
			    		}
			    		
			    		tmpRate = tmpRate / (double)numMod;
			    		
			    		if (tmpRate < minRate) {
			    			minClosure = allocModClosure;
			    			minRate = tmpRate;
			    		}
	    			}
	    		}
	    		
	    		
	    	}
    	
    	
//	    	toDealloc = minClosure;
//	    	for (String k : minClosure) {
//	    		System.out.println("kkkk    "+k);
//	    	}
//	    	System.out.println("kkkk    "+minRate);
//	    	return minRate;
    	
    		Pair<Double,List<String>> myPair = new Pair<Double,List<String>>(minRate,minClosure);
//    	
    		return myPair;
    		

    }
    
    protected double calculateModuleRate(FogDevice dev, String modName) {

		double totalRate = 0.0;
		
		HashMap<String, Double> deviceRequestRate = mapDeviceSensorRate.get(dev.getName());
		HashMap<String, Double> modRequestRate = mapModRateValues.get(modName);
		
		
		for (Map.Entry<String, Double> modMapEntry : modRequestRate.entrySet()) {
			Double deviceRate;
			if ( (deviceRate = deviceRequestRate.get(modMapEntry.getKey()))!=null ) {
				totalRate += deviceRate * modMapEntry.getValue();
			}
		}
		
	
		return totalRate;
    	
    	
    }
    
    protected HashMap<String,Double> calculateModuleRateAllocatedMod(FogDevice dev){
    	
	    	HashMap<String,Double> allocModRates = new HashMap<String,Double>(); 
	    	
	    	List<String> currentModules = currentModuleMap.get(dev);
//	    	System.out.println("++calculateModuleRateAllocatedMod for dev "+dev.getName());
	    	for (String mod : currentModules) {
//	    		System.out.print(mod+"    ");
	    		Double temp = calculateModuleRate(dev,mod);
	    		allocModRates.put(mod, temp);
	    	}
//	    	System.out.println("");
//	    	System.out.println("++END calculateModuleRateAllocatedMod for dev "+dev.getName());
	    	
	    	
	    	return allocModRates;
    }
    
    
    protected void atToPendingList(Pair<FogDevice,String> pair) {
    		if (!modulesToPlace.contains(pair)) {
    			modulesToPlace.add(pair);  
    		}
    		
    }
    
    protected void sendToFather(FogDevice dev, String modName, double resourcesStilNeeded) {
    	
    		Pair<FogDevice,String> pairFather;
    		FogDevice father = getFogDeviceById(dev.getParentId());
    		
    		List<String> invertedClosure = new ArrayList<String>();
    		
    		for (String consummedMod : mapModuleClosure.get(modName)) {
    			invertedClosure.add(consummedMod);
    		}
    		
    		
    		Collections.reverse(invertedClosure);
    		
    		for (String consummedMod : invertedClosure) {
    			if ( (currentModuleMap.get(dev).contains(consummedMod)) && (resourcesStilNeeded > 0.0) ) {
	    	    		pairFather = new Pair<FogDevice,String>(father,consummedMod);
	    	    		atToPendingList(pairFather);			
	    	    		currentModuleMap.get(dev).remove(consummedMod);
	    	    		
//	    	    		System.out.println("Removed from pre-allocated list the module "+consummedMod+" in device "+dev.getName()+" due to a sendToFather of a preconsummed service");
    				
	    	    		Double freededRes = calculateResourceUsage(dev,consummedMod);
	    	    		Double currentRes = currentCpuLoad.get(dev);
	    	    		currentRes -= freededRes;
	    	    		currentCpuLoad.put(dev, currentRes);
	    	    		resourcesStilNeeded -= freededRes;
	    	    		
    			}
    		}
    		

    		//DEBEMOS DE VOLVER A PROBAR DE COLOCAR EL MODULE YA QUE QUIZAS LOS QUE ACABO DE ELIMINAR ERAN DE MAYOR REQUEST, PERO AL ELIMINARLS, ME QUEDAN RESOURCES
    		if (resourcesStilNeeded > 0) {  //Solo lo enviamos al padre si quitando los del clousure no hemos liberado suficiente 
	    		pairFather = new Pair<FogDevice,String>(father,modName);
	    		atToPendingList(pairFather);
			modulesToPlace.remove(0);
    		} //El else sería que sí que hemos eliminado suficientes, y tendríamos que volver a mirar si lo alojamos. Basta simplemente no eliminar el elemento del modulestoplace y se volvera a evaluar su allocation en este device.
    		else {
//    			System.out.println("Module "+modName+" in device "+dev.getName()+" keeped to the next iteration due to preconsummed service deallocations");
    		}
    		
    		
    }
    
    protected void Placement() {
    	
    		int numOptExecutions = 0;
    		

    	
    		while (modulesToPlace.size()>0) {
    			
//    			System.out.println("===================================== "+numOptExecutions);
    			numOptExecutions ++;
    			Pair<FogDevice,String> pair = modulesToPlace.get(0);
    			FogDevice dev = pair.getFirst();
    			String modName = pair.getSecond();
    			int devMips = dev.getHost().getTotalMips();
    			Double currentMips = currentCpuLoad.get(dev);
    			
//    			System.out.println("Starting with allocation of module "+modName+" in device "+dev.getName());
    			

    			
    			List<String> currentModules = currentModuleMap.get(dev);
    			if (currentModules == null) {
    				currentModules = new ArrayList<String>();
    				currentModuleMap.put(dev, currentModules);
    			}
    			
    			

			if (currentModules.contains(modName)) { //The module is already in the device
				//TODO Update the usage ?????
//				System.out.println("Module "+modName+" in device "+dev.getName()+" already allocated, so removed form toAllocate list");
				modulesToPlace.remove(0);
			}else {
				if (dev.getLevel()==0) {
//					System.out.println("Module "+modName+" in device "+dev.getName()+" allocated because the device is the cloud");

					currentModules.add(modName);//the device is the cloud
					numOfCloudPlacements++;
					modulesToPlace.remove(0);
				}else {
					double requiredResources = calculateResourceUsage(dev,modName);
//		    			System.out.println("Total dev available CPU MIPS "+devMips);
//		    			System.out.println("Already allocated   CPU MIPS "+currentMips);
//		    			System.out.println("Module required     CPU MIPS "+requiredResources);

					
					if (devMips < requiredResources) {
//						System.out.println("Module "+modName+" in device "+dev.getName()+" send to father because total resources not enough");
//				    		Pair<FogDevice,String> pairFather = new Pair<FogDevice,String>(getFogDeviceById(dev.getParentId()),modName);
//				    		modulesToPlace.add(pairFather);
						sendToFather(dev, modName, Double.MAX_VALUE);
//						modulesToPlace.remove(0);
						
					}else {
						
						double availableMips = devMips - currentMips;
						
						if ( availableMips > requiredResources) {
//							System.out.println("Module "+modName+" in device "+dev.getName()+" allocated because enough resources");

							PreAllocate(dev,modName);
							currentCpuLoad.put(dev, currentMips + requiredResources);
//							System.out.println("Pre-allocated module "+modName+" in device "+dev.getName());
							modulesToPlace.remove(0);
							
						}else {
					
							List<String> candidatesToDeallocate = new ArrayList<String>();
							double deallocatedResources = 0.0;
							
							
							double moduleRate = calculateModuleRate(dev, modName);
							
							HashMap<String,Double> allocModRate = calculateModuleRateAllocatedMod(dev);
							

							
							
							
							boolean enoughWithDeallocation = true;
							
							while ( enoughWithDeallocation && ( (availableMips + deallocatedResources)< requiredResources ) ){
								List<String> toDealloc = new ArrayList<String>();
								Pair<Double,List<String>> deallocPair = DeallocateLowerRequestedClosure(dev, modName, candidatesToDeallocate, allocModRate);
								double deallocRat = deallocPair.getFirst();
								toDealloc = deallocPair.getSecond();
								
//								double deallocRat = DeallocateLowerRequestedClosure(toDealloc, dev, candidatesToDeallocate, allocModRate);
//							    	for (String k : toDealloc) {
//							    		System.out.println("22kkkk    "+k);
//							    	}
//							    	System.out.println("22kkkk    "+deallocRat);
								
								if (deallocRat < moduleRate) {
									double deallocRes = calculateClosureRes(dev,toDealloc);
									deallocatedResources += deallocRes;
									for (String s : toDealloc) {
										candidatesToDeallocate.add(s);
									}
									
								}else {
									
									enoughWithDeallocation = false;
									
								}
							}
								
							if (enoughWithDeallocation) {
//								System.out.println("Module "+modName+" in device "+dev.getName()+" allocated because other module closures with smaller rates are deallocated");

								
								for (String toDeallocMod : candidatesToDeallocate) {  //Every element in the toDeallocatedMod in the list is send to the father and removed from the currentModuleMap. The usage update is donde after the for with the already calculated usages values.
									Pair<FogDevice,String> pairFather = new Pair<FogDevice,String>(getFogDeviceById(dev.getParentId()),toDeallocMod);
									atToPendingList(pairFather);
									currentModuleMap.get(dev).remove(toDeallocMod);
									System.out.println("Removed from pre-allocated list the module "+toDeallocMod+" in device "+dev.getName());
									numOfMigrations++;
								}
								currentMips -= deallocatedResources;  //the deallocated resources are restados.
								
								PreAllocate(dev,modName);
								currentCpuLoad.put(dev, currentMips + requiredResources);
//								System.out.println("Pre-allocated module "+modName+" in device "+dev.getName());
								modulesToPlace.remove(0);									
								
							}else {
//								System.out.println("Module "+modName+" in device "+dev.getName()+" send to parent because not enough resources deallocating other closures");

//						    		Pair<FogDevice,String> pairFather = new Pair<FogDevice,String>(getFogDeviceById(dev.getParentId()),modName);
//						    		modulesToPlace.add(pairFather);								
								sendToFather(dev, modName, requiredResources-availableMips);
//								modulesToPlace.remove(0);

							}
							


							
						}
					
					}
					
					
					
					////////totalCpuLoad += rate*edge.getTupleCpuLength();
					
					//// Valor de uso de CPU si no hay nigun device por debajo = 
					//// Número de veces que se llama a la aplicación desde cualquier nodo leaf que cuelgue de este device
					//// *
					//// Número de veces que se recibe un edge en este modulo por cada llamada de la aplicación
					//// *
					//// CPU que consume dicho edge
					
					
				}
			}
		
    			
    			
    			
    		}
    }
   

    @Override
    protected void mapModules() {
    	
    		
    	
		for (FogDevice gw : gateways) {
			for(String modName : moduleOrder) {
				Pair<FogDevice,String> pair = new Pair<FogDevice,String>(gw,modName);
				atToPendingList(pair);
			}
		}
		
		Placement();
        
		for(FogDevice dev : currentModuleMap.keySet()){
			for(String module : currentModuleMap.get(dev)){
				createModuleInstanceOnDevice(getApplication().getModuleByName(module), dev);
			}
		}

    }

    
    protected void searchGateways() {
    	
    		for (FogDevice device : getFogDevices()) {
    			if (device.getName().startsWith("m-")) {
    				gateways.add(getFogDeviceById(device.getParentId()));
    			}
    		}
    		
//    		for (FogDevice gwName : gateways) {
//    			System.out.println("OOOOOO :"+gwName.getName());
//    		}
    	
    }
    

    protected void calculateClosure() {
    		for (AppModule module : getApplication().getModules()) {
    			List<String> moduleClosure = new ArrayList<String>();
    			
    			addRecursiveChildren(module.getName(),moduleClosure);
    			
    			Collections.reverse(moduleClosure);

    			mapModuleClosure.put(module.getName(), moduleClosure);
    		}
    		
    		
//        for (Map.Entry<String, List<String>> entry : mapModuleClosure.entrySet()) {
//        	
//        		System.out.println("CAAAAA: "+entry.getKey());
//	    		for (String el : entry.getValue()) {
//	    			System.out.println("########## "+el);
//	    		}       	
//        	
//        }
    		

    		
    		
    }
    
    
    protected void addRecursiveChildren(String moduleName, List<String> mylist) {
    	
    		for (AppEdge edge : getApplication().getEdges()) {
    			if (edge.getSource().equals(moduleName)) {
    				if (!mylist.contains(moduleName)) {
    					addRecursiveChildren(edge.getDestination(),mylist);
    				}
    			}
    		}
    		if (!mylist.contains(moduleName)) {
    			mylist.add(moduleName);
    		}
    	
    }
    
    protected void calculateModuleOrder() {
    	

		for (AppEdge edge : getApplication().getEdges()) {
			if (edge.getEdgeType()==AppEdge.SENSOR) {
				addRecursiveChildren(edge.getDestination(),moduleOrder);
			}
		}
	
		
		//REALMENTE EL ORDEN A HACER EL PLACEMENT DEBERIA SER DE LOS NO DEPENDIENTES HACIA ATRAS, ASI QUE NO TENGO QUE GIRAR EL LISTADO
		
//		Collections.reverse(moduleOrder);

//    		for (String el : moduleOrder) {
//    			System.out.println("########## "+el);
//    		}  
//			
			
    	
    }
    
    protected void pathEdgeRate(AppEdge pathEdgeInput, AppEdge currentEdge, double currentRate) {
    	
    		AppModule currentModule = getApplication().getModuleByName(currentEdge.getDestination());
    		for (AppEdge edge : getApplication().getEdges()) {
    			
    			if (edge.getSource().equals(currentModule.getName())
    				&& edge.getEdgeType()!=AppEdge.ACTUATOR ){
    				
    				SelectivityModel s = currentModule.getSelectivityMap().get(new Pair<String, String>(currentEdge.getTupleType(), edge.getTupleType()));
    				if (s!=null) {
    					double newRate = currentRate * s.getMeanRate();
		        		HashMap<String, Double> mapTargetEdge = null;
		        		if ( ( mapTargetEdge = mapEdgeRateValues.get(edge.getTupleType())) != null ) {
		        			Double finalvalue;
		        			if ( (finalvalue = mapTargetEdge.get(pathEdgeInput.getTupleType())) != null ){
		        				finalvalue += newRate;
		        				mapTargetEdge.put(pathEdgeInput.getTupleType(), finalvalue);
		        			} else {
		        				mapTargetEdge.put(pathEdgeInput.getTupleType(), newRate);
		        			}
		        		}else {
		        			
		        			mapTargetEdge = new HashMap<String, Double>();
		        			mapTargetEdge.put(pathEdgeInput.getTupleType(), newRate);
		        			mapEdgeRateValues.put(edge.getTupleType(), mapTargetEdge);
		        		}		        			

		        		
		        		pathEdgeRate(pathEdgeInput,edge,newRate);
		    			
    					
    					
    				}
    				
    			}
    		}
    }
    
    
    protected void pathModRate(AppModule pathModuleInput, AppModule currentModule, double currentRate, String incomingTupleType, String initialTupleType) {
    		for (AppEdge edge : getApplication().getEdges()) {
    			if (edge.getSource().equals(currentModule.getName())
    				&& edge.getEdgeType()!=AppEdge.ACTUATOR ){
  
//    				System.out.println("Current:"+currentModule.getName());
//    				System.out.print(edge.getTupleType()+"::::");
//    				System.out.print(edge.getSource());
//    				System.out.print("====>");
//    				System.out.println(edge.getDestination());
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
    
    protected void calculateEdgeRate() {
        	for (AppEdge edge : getApplication().getEdges()) {
    			if (edge.getEdgeType()==AppEdge.SENSOR ){
	        		double currentRate = 1.0;
	  
	        		String initialTupleType = edge.getTupleType();
	        		
	        		HashMap<String, Double> mapTargetEdge = null;
	        		if ( ( mapTargetEdge = mapEdgeRateValues.get(edge.getTupleType())) != null ) {
	        			Double finalvalue;
	        			if ( (finalvalue = mapTargetEdge.get(initialTupleType)) != null ){
	        				finalvalue += currentRate;
	        				mapTargetEdge.put(initialTupleType, finalvalue);
	        			} else {
	        				mapTargetEdge.put(initialTupleType, currentRate);
	        			}
	        			
	        			
	        		}else {
	        			
	        			mapTargetEdge = new HashMap<String, Double>();
	        			mapTargetEdge.put(initialTupleType, currentRate);
	        			mapEdgeRateValues.put(edge.getTupleType(), mapTargetEdge);
	        		}
	        		
	        		pathEdgeRate(edge,edge,currentRate);
    			}
        	}
        	
        	
//        	for (Map.Entry<String, HashMap<String,Double>> outerentry : mapEdgeRateValues.entrySet()) {
//        		System.out.println("Edge destino=" + outerentry.getKey());
//        		HashMap<String,Double> innermap = outerentry.getValue();
//        		for (Map.Entry<String, Double> innerentry : innermap.entrySet()) {
//        			System.out.println("++++Edge leaf=" + innerentry.getKey() + ", valor=" + innerentry.getValue());
//
//        		}
//        	}        	
        	
        	
        	
        	
        	
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
        

        
        
        
//        for (Map.Entry<String, HashMap<String,Double>> outerentry : mapModRateValues.entrySet()) {
//            System.out.println("Modulo destino=" + outerentry.getKey());
//            HashMap<String,Double> innermap = outerentry.getValue();
//            for (Map.Entry<String, Double> innerentry : innermap.entrySet()) {
//            		System.out.println("++++Modulo leaf=" + innerentry.getKey() + ", valor=" + innerentry.getValue());
//            	
//            }
//        }
        
        
        
        
        
    }
    
	public List<Sensor> getSensors() {
		return sensors;
	}
	
    private void setAssociatedSensors(FogDevice device) {
		
		for(Sensor sensor : getSensors()){
			if(sensor.getGatewayDeviceId()==device.getId()){
				double meanT = sensor.getTransmitDistribution().getMeanInterTransmitTime();
				meanT = 1.0 / meanT;
				
				String initialTupleType = sensor.getTupleType();
				
	        		HashMap<String, Double> mapDeviceModule = null;
	        		if ( ( mapDeviceModule = mapDeviceSensorRate.get(device.getName())) != null ) {
	        			Double finalvalue;
	        			if ( (finalvalue = mapDeviceModule.get(initialTupleType)) != null ){
	        				finalvalue += meanT;
	        				mapDeviceModule.put(initialTupleType, finalvalue);
	        			} else {
	        				mapDeviceModule.put(initialTupleType, meanT);
	        			}
	        			
	        			
	        		}else {
	        			
	        			mapDeviceModule = new HashMap<String, Double>();
	        			mapDeviceModule.put(initialTupleType, meanT);
	        			mapDeviceSensorRate.put(device.getName(), mapDeviceModule);
	        		}
				
				
				
				
//				deviceSensorRate[sensor.getId()][device.getId()]+= meanT;
			}
		}
		
	}   
    
    protected void sumChildrenRates(FogDevice dev) {
    	
		HashMap<String,Double> mapDev;
		
		if ( (mapDev = mapDeviceSensorRate.get(dev.getName()))== null ) {
			mapDev = new HashMap<String,Double>();
			mapDeviceSensorRate.put(dev.getName(),mapDev);
		}
    	
    	
    		for (Integer chdDevId : dev.getChildrenIds()) {
    			FogDevice chdDev = getFogDeviceById(chdDevId);
    			sumChildrenRates(chdDev);    			
    			

    			
    			//No importa comprobar si está o no, si no está es que no habría hecho el recorrido en profundidad, pues al 
    			//hacer el recorrido lo primero es crearlo si no existe (ver las primeras lineas de este metodo)
    			HashMap<String,Double> mapChdSensor = mapDeviceSensorRate.get(chdDev.getName());
            for (Map.Entry<String, Double> sensor : mapChdSensor.entrySet()) {
            		String sensorName = sensor.getKey();
            		Double sensorValue = sensor.getValue();
            		Double storedvalue;
            		if ( (storedvalue = mapDev.get(sensorName)) != null) {
            			mapDev.put(sensorName, sensorValue + storedvalue);
//            			System.out.println("Le sumo al device "+dev.getName()+" el valor "+sensorValue+" de su hijo "+chdDev.getName()+" con su valor actual "+storedvalue+" para el sensor "+sensorName);
            		}else {
            			mapDev.put(sensorName, sensorValue);
//            			System.out.println("Le fijo al device "+dev.getName()+" el valor "+sensorValue+" de su hijo "+chdDev.getName()+" para el sensor "+sensorName);
                		
            		}
            		

            }
    			
    		}
    	
    	
    	
    	
    		
//    		for (Integer chdDevId : dev.getChildrenIds()) {
//    			
//    			FogDevice chdDev = getFogDeviceById(chdDevId);
//    			sumChildrenRates(chdDev, numSensors);
//    			System.out.println(dev.getName()+"::"+dev.getId());
//    			System.out.println(chdDev.getName()+"::"+chdDev.getId());
//        		for (int i=0;i<numSensors;i++) {
//        			deviceSensorRate[i][dev.getId()]+=deviceSensorRate[i][chdDevId];
//        			}
//        		System.out.println("");
//    			
//    		}
    	
    }
    
    protected void calculateDeviceSensorDependencies() {

    		int maxDevId = 0;
    		List<FogDevice> leafDevices = new ArrayList<FogDevice>();
    		FogDevice rootDev = null;

    		for(FogDevice dev : getFogDevices()){
    			
			if (dev.getId()>maxDevId)
				maxDevId = dev.getId();
			if (dev.getChildrenIds().isEmpty()) {
				leafDevices.add(dev);
			}
			if (dev.getLevel()==0) {
				rootDev = dev;
			}
				
			
		}
//    		maxDevId ++;
//    	
//    		int maxSenId = 0;
//    		for(Sensor sen : sensors){
//			if (sen.getId()>maxSenId)
//				maxSenId = sen.getId();
//		}
//    		maxSenId ++;

//    		deviceSensorRate = new double[maxSenId][maxDevId];
//
//    		for (int i=0; i<maxSenId; i++) {
//    			for (int j=0; j<maxDevId; j++) {
//    				deviceSensorRate[i][j]=0.0;
//    			}
//    		}
    		
    		for (FogDevice dev : leafDevices) {
    			setAssociatedSensors(dev);
    		}
    		
    		sumChildrenRates(rootDev);
    		
//        for(int i = 0; i < maxSenId; i++)
//        {
//           for(int j = 0; j < maxDevId; j++)
//           {
//              System.out.printf(deviceSensorRate[i][j]+"|");
//           }
//           System.out.println();
//        }
        
        
//        for (Map.Entry<String, HashMap<String,Double>> outerentry : mapDeviceSensorRate.entrySet()) {
//            System.out.println("Device=" + outerentry.getKey());
//            HashMap<String,Double> innermap = outerentry.getValue();
//            for (Map.Entry<String, Double> innerentry : innermap.entrySet()) {
//            		System.out.println("++++Sensor=" + innerentry.getKey() + ", valor=" + innerentry.getValue());
//            	
//            }
//        }
        
        
        
    		
    }
    

}
