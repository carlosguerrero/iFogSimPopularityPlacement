package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for case study 1 - EEG Beam Tractor Game
 * @author Harshit Gupta
 *
 */
public class NApps {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<FogDevice> mobiles = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	
	static int numOfDepts = 2;
	static int numOfMobilesPerDept = 4;
	static int numOfNetworkLevels = 2;
	static double EEG_TRANSMISSION_TIME = 5.1;
	static int numOfApps = 1;
	//static double EEG_TRANSMISSION_TIME = 10;
	
	public static void main(String[] args) {

		Log.printLine("Starting TwoApps...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);
			
			
			String[] appId = new String[numOfApps];
			FogBroker[] broker = new FogBroker[numOfApps];
			Application[] application = new Application[numOfApps];
			ModuleMapping[] moduleMapping = new ModuleMapping[numOfApps];
			
			
			createFogDevices();
			
			int currentApp=0;
			
			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				appId[currentApp] = "vr_game_"+currentApp;
			}

			
			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				broker[currentApp] = new FogBroker("broker_"+currentApp);
			}

			
			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				application[currentApp] = createApplication(appId[currentApp], broker[currentApp].getId());
			}

			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				application[currentApp].setUserId(broker[currentApp].getId());
			}

			
			
			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				createEdgeDevices(broker[currentApp].getId(), appId[currentApp]);
			}

			
			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				moduleMapping[currentApp] = ModuleMapping.createModuleMapping();
			}

			
			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				moduleMapping[currentApp].addModuleToDevice("connector"+appId[currentApp], "cloud");
				moduleMapping[currentApp].addModuleToDevice("concentration_calculator"+appId[currentApp], "cloud");
			}
			
			
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("m")){
					for (currentApp=0;currentApp<numOfApps;currentApp++) {
						moduleMapping[currentApp].addModuleToDevice("client"+appId[currentApp], device.getName());
					}
				}
			}
			
			Controller controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			
			for (currentApp=0;currentApp<numOfApps;currentApp++) {
				controller.submitApplication(application[currentApp], new ModulePlacementMapping(fogDevices, application[currentApp], moduleMapping[currentApp]));
			}
			
			 
			for (FogDevice f : fogDevices) {
				System.out.println(f.getId() +":"+f.getName()+":level="+f.getLevel());
			}

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			CloudSim.terminateSimulation(1000);  //termina la simulación tras 10 ms
			
			CloudSim.startSimulation();
			
//			CloudSim.finishSimulation();
//			CloudSim.runStop();

			CloudSim.stopSimulation();
			

			
			controller.printresults();
			/*Public methor in class controller that executes
			 * printTimeDetails()
			 * printPowerDetails()
			 * printCostDetails()
			 * printNetworkUsageDetails()
			 */

			Log.printLine("VRGame finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}

	private static void createEdgeDevices(int userId, String appId) {
		for(FogDevice mobile : mobiles){
			String id = mobile.getName();
			Sensor eegSensor = new Sensor("s-"+appId+"-"+id, "EEG"+appId, userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
			sensors.add(eegSensor);
			Actuator display = new Actuator("a-"+appId+"-"+id, userId, appId, "DISPLAY"+appId);
			actuators.add(display);
			eegSensor.setGatewayDeviceId(mobile.getId());
			eegSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
			display.setGatewayDeviceId(mobile.getId());
			display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms			
		}
	}
	
//	private static void createEdgeDevices1(int userId, String appId) {
//		for(FogDevice mobile : mobiles){
//			String id = mobile.getName();
//			Sensor eegSensor = new Sensor("s-"+appId+"-"+id, "EEG_1", userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
//			sensors.add(eegSensor);
//			Actuator display = new Actuator("a-"+appId+"-"+id, userId, appId, "DISPLAY_1");
//			actuators.add(display);
//			eegSensor.setGatewayDeviceId(mobile.getId());
//			eegSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
//			display.setGatewayDeviceId(mobile.getId());
//			display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms			
//		}
//	}

	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices() {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25); // creates the fog device Cloud at the apex of the hierarchy with level=0
		cloud.setParentId(-1);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
		
		fogDevices.add(cloud);
		fogDevices.add(proxy);
		
		for(int i=0;i<numOfDepts;i++){
			addGw(i+"", proxy.getId(),1); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
		}
		
	}

	private static FogDevice addGw(String id, int parentId, int currentNetLevel){
		FogDevice dept = createFogDevice("d-"+id, 2800, 4000, 10000, 10000, 1+currentNetLevel, 0.0, 107.339, 83.4333);
		fogDevices.add(dept);
		dept.setParentId(parentId);
		dept.setUplinkLatency(4); // latency of connection between gateways and proxy server is 4 ms
		if (currentNetLevel == numOfNetworkLevels) {
			for(int i=0;i<numOfMobilesPerDept;i++){
				String mobileId = id+"-"+i;
				FogDevice mobile = addMobile(mobileId, dept.getId(),2+currentNetLevel); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
				
				mobile.setUplinkLatency(2); // latency of connection between the smartphone and proxy server is 4 ms
				fogDevices.add(mobile);
			}
		}else {
			for(int i=0;i<numOfDepts;i++){
				addGw(id+"-"+i+"", dept.getId(),currentNetLevel+1); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
			}
		}
		return dept;
	}
	
	private static FogDevice addMobile(String id, int parentId, int netLevel){
		FogDevice mobile = createFogDevice("m-"+id, 1000, 1000, 10000, 270, netLevel, 0, 87.53, 82.44);
		mobile.setParentId(parentId);
		mobiles.add(mobile);
		/*Sensor eegSensor = new Sensor("s-"+id, "EEG", userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
		sensors.add(eegSensor);
		Actuator display = new Actuator("a-"+id, userId, appId, "DISPLAY");
		actuators.add(display);
		eegSensor.setGatewayDeviceId(mobile.getId());
		eegSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
		display.setGatewayDeviceId(mobile.getId());
		display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
*/		return mobile;
	}
	
	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the EEG Tractor Beam game application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)
		
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("client"+appId, 10); // adding module Client to the application model
		application.addAppModule("concentration_calculator"+appId, 10); // adding module Concentration Calculator to the application model
		application.addAppModule("connector"+appId, 10); // adding module Connector to the application model
		
		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		if(EEG_TRANSMISSION_TIME==10)
			application.addAppEdge("EEG"+appId, "client"+appId, 2000, 500, "EEG"+appId, Tuple.UP, AppEdge.SENSOR); // adding edge from EEG (sensor) to Client module carrying tuples of type EEG
		else
			application.addAppEdge("EEG"+appId, "client"+appId, 3000, 500, "EEG"+appId, Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("client"+appId, "concentration_calculator"+appId, 3500, 500, "_SENSOR"+appId, Tuple.UP, AppEdge.MODULE); // adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
		application.addAppEdge("concentration_calculator"+appId, "connector"+appId, 100, 1000, 1000, "PLAYER_GAME_STATE"+appId, Tuple.UP, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Concentration Calculator to Connector module carrying tuples of type PLAYER_GAME_STATE
		application.addAppEdge("concentration_calculator"+appId, "client"+appId, 14, 500, "CONCENTRATION"+appId, Tuple.DOWN, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
		application.addAppEdge("connector"+appId, "client"+appId, 100, 28, 1000, "GLOBAL_GAME_STATE"+appId, Tuple.DOWN, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Connector to Client module carrying tuples of type GLOBAL_GAME_STATE
		application.addAppEdge("client"+appId, "DISPLAY"+appId, 1000, 500, "SELF_STATE_UPDATE"+appId, Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
		application.addAppEdge("client"+appId, "DISPLAY"+appId, 1000, 500, "GLOBAL_STATE_UPDATE"+appId, Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE
		
		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		application.addTupleMapping("client"+appId, "EEG"+appId, "_SENSOR"+appId, new FractionalSelectivity(0.9)); // 0.9 tuples of type _SENSOR are emitted by Client module per incoming tuple of type EEG 
		application.addTupleMapping("client"+appId, "CONCENTRATION"+appId, "SELF_STATE_UPDATE"+appId, new FractionalSelectivity(1.0)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION 
		application.addTupleMapping("concentration_calculator"+appId, "_SENSOR"+appId, "CONCENTRATION"+appId, new FractionalSelectivity(1.0)); // 1.0 tuples of type CONCENTRATION are emitted by Concentration Calculator module per incoming tuple of type _SENSOR 
		application.addTupleMapping("client"+appId, "GLOBAL_GAME_STATE"+appId, "GLOBAL_STATE_UPDATE"+appId, new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE 
	
		/*
		 * Defining application loops to monitor the latency of. 
		 * Here, we add only one loop for monitoring : EEG(sensor) -> Client -> Concentration Calculator -> Client -> DISPLAY (actuator)
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("EEG"+appId);add("client"+appId);add("concentration_calculator"+appId);add("client"+appId);add("DISPLAY"+appId);}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);
		
		return application;
	}
	
//	@SuppressWarnings({"serial" })
//	private static Application createApplication1(String appId, int userId){
//		
//		Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)
//		
//		/*
//		 * Adding modules (vertices) to the application model (directed graph)
//		 */
//		application.addAppModule("client_1", 10); // adding module Client to the application model
//		application.addAppModule("concentration_calculator_1", 10); // adding module Concentration Calculator to the application model
//		application.addAppModule("connector_1", 10); // adding module Connector to the application model
//		
//		/*
//		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
//		 */
//		if(EEG_TRANSMISSION_TIME==10)
//			application.addAppEdge("EEG_1", "client_1", 2000, 500, "EEG_1", Tuple.UP, AppEdge.SENSOR); // adding edge from EEG (sensor) to Client module carrying tuples of type EEG
//		else
//			application.addAppEdge("EEG_1", "client_1", 3000, 500, "EEG_1", Tuple.UP, AppEdge.SENSOR);
//		application.addAppEdge("client_1", "concentration_calculator_1", 3500, 500, "_SENSOR_1", Tuple.UP, AppEdge.MODULE); // adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
//		application.addAppEdge("concentration_calculator_1", "connector_1", 100, 1000, 1000, "PLAYER_GAME_STATE_1", Tuple.UP, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Concentration Calculator to Connector module carrying tuples of type PLAYER_GAME_STATE
//		application.addAppEdge("concentration_calculator_1", "client_1", 14, 500, "CONCENTRATION_1", Tuple.DOWN, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
//		application.addAppEdge("connector_1", "client_1", 100, 28, 1000, "GLOBAL_GAME_STATE_1", Tuple.DOWN, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Connector to Client module carrying tuples of type GLOBAL_GAME_STATE
//		application.addAppEdge("client_1", "DISPLAY_1", 1000, 500, "SELF_STATE_UPDATE_1", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
//		application.addAppEdge("client_1", "DISPLAY_1", 1000, 500, "GLOBAL_STATE_UPDATE_1", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE
//		
//		/*
//		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
//		 */
//		application.addTupleMapping("client_1", "EEG_1", "_SENSOR_1", new FractionalSelectivity(0.9)); // 0.9 tuples of type _SENSOR are emitted by Client module per incoming tuple of type EEG 
//		application.addTupleMapping("client_1", "CONCENTRATION_1", "SELF_STATE_UPDATE_1", new FractionalSelectivity(1.0)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION 
//		application.addTupleMapping("concentration_calculator_1", "_SENSOR_1", "CONCENTRATION_1", new FractionalSelectivity(1.0)); // 1.0 tuples of type CONCENTRATION are emitted by Concentration Calculator module per incoming tuple of type _SENSOR 
//		application.addTupleMapping("client_1", "GLOBAL_GAME_STATE_1", "GLOBAL_STATE_UPDATE_1", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE 
//	
//		/*
//		 * Defining application loops to monitor the latency of. 
//		 * Here, we add only one loop for monitoring : EEG(sensor) -> Client -> Concentration Calculator -> Client -> DISPLAY (actuator)
//		 */
//		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("EEG_1");add("client_1");add("concentration_calculator_1");add("client_1");add("DISPLAY_1");}});
//		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
//		application.setLoops(loops);
//		
//		return application;
//	}
	

	
	
}