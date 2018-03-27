package javaapi;

import javaapi.TestFlow;
import javaapi.Seq_Test_Flow;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.advantest.kei.KSystemVariable;
import com.advantest.kei.KDeviceTestProgram;
import com.advantest.kei.KTestItemRegistry;
import com.advantest.kei.KTestItem;
import com.advantest.kei.IKNoMoreTargetDutAction;
import com.advantest.kei.KVariableRegistry;
import com.advantest.kei.KVariableRegistry;
import com.advantest.kei.KNoMoreTargetDutException;

public class Agent extends KDeviceTestProgram implements IKNoMoreTargetDutAction{
	final private String defaultSysVar = "ECOTS_SD_TPCLASS";
	private KDeviceTestProgram currentTp;

	@Override public void initializeVariableRegistry(KVariableRegistry arg0) {
		//   /opt/ATFS/arm/linux/ATFSkss-1.01/T5831/lib
		//System.out.println("===========================\n" + System.getProperty("java.library.path"));
		this.currentTp = newInstanceFromSysVar();
		this.currentTp.initializeVariableRegistry(arg0);
	}

	@Override public void initializeTestItemRegistry(KTestItemRegistry registry) {
		this.currentTp.initializeTestItemRegistry(registry);
	}

	@Override public void testStartAction() throws KNoMoreTargetDutException {
		this.currentTp.testStartAction();

		if(Inject.isTargetFlow(SS.testStep)) {
			DeviceInfo.datalogOutput = false;
			CacheTB.init();
			setTestStartTime();
			setFlowName(SS.testStep);

			for(;;) {
				try {
					String msg = Signal.poll();
					TestFlow[] tf = CacheTB.query(CacheTB.parse(msg));
					Inject.into(this).update(new TestItemList().getList(tf));
					break;
				} catch(Exception e) {
					System.out.println("\n\n" + e);
					Signal.lazyReset();
				}
			}
			DeviceInfo.datalogOutput = true;
		}
	}

	@Override public void programStartAction() {
		this.currentTp.programStartAction();
	}

	@Override public void testEndAction() throws Exception {
		this.currentTp.testEndAction();
	}

	@Override public void noMoreTargetDutAction() {
		System.out.printf("NoMoreTargetDutException Test End\n");
		TestItem end;
		try {
			end = new EndOfFlow("End Of Flow", true);
			((TestItem)getCurrentTestItem()).setCategory();
			end.body();
		} catch (Exception e) {
			//empty
		}
	}

	private void setTestStartTime() {
		try {
			Field fd = KDeviceTestProgram.class.getDeclaredField("testStartTime");
			fd.set(this.currentTp, System.currentTimeMillis());
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private KDeviceTestProgram newInstanceFromSysVar(String... vars) {
		String sysVar = vars.length == 0 ? defaultSysVar : vars[0];
		try {
			Class<?> cls = Class.forName(
					KSystemVariable.read(sysVar)
					.replaceAll(".class", "")
					.replaceAll("/", ".")); //"javaapi.tea3628sh0401_192b"
			return (KDeviceTestProgram)cls.newInstance();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}

class CacheTB {
	final private static Class flowClass = Seq_Test_Flow.class;
	private static Map<String, Map<String, TestFlow>> map;
	final private static String[] flowOrder = {
		"FH_Flow",
		"FR_Flow",
		"FL_Flow",
		"SH_Flow",
		"CFH_Flow",
		"CSH_Flow",
		"QH_Flow",
		"QR_Flow",
		"QL_Flow",
		"CH_Flow",
		"CR_Flow",
		"CL_Flow",
		"Debug_Flow",
		"DebugPats_Flow",
	};

	final public static void init() {
		if(map != null) {
			return;
		}
		map = new HashMap<String, Map<String, TestFlow>>();
		for(String flow : flowNames()) {
			Map<String, TestFlow> inFlowMap = new HashMap<String, TestFlow>();
			for(TestFlow tb : itemsOf(flow)) {
				inFlowMap.put(tb.testName, tb);
			}
			map.put(flow, inFlowMap);
		}
		stat();
	}

	final public static String[] parse(String msg) {
		return msg.trim().split(";");
	}

	final public static TestFlow[] query(String... names) throws Exception {
		TestFlow[] tfs = new TestFlow[names.length];
		for(int i = 0; i < names.length; i++) {
			String name = names[i].trim();
			TestFlow tf = searchInFlows(name);
			if(tf == null) {
				throw new Exception("Unknown tb: " + name);
			}
			tfs[i] = tf;
		}
		return tfs;
	}

	private static TestFlow searchInFlows(String name) throws Exception {
		for(String flow : flowOrder) {
			if(! map.containsKey(flow)) {
				continue;
			}
			TestFlow tf = map.get(flow).get(name);
			if(tf != null) {
				System.out.printf("%s from %s\n", tf.testName, flow);
				printFlowItem(tf);
				return tf;
			}
		}
		return null;
	}

	private static void printFlowItem(TestFlow tf) {
		Field[] fds = TestFlow.class.getDeclaredFields();
		for(Field fd : fds) {
			fd.setAccessible(true);
			try {
				if(! "testName".equals(fd.getName())) {
					System.out.printf("\t%s: %s\n", fd.getName(), String.valueOf(fd.get(tf)));
				}
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private static void stat() {
		System.out.println("\t[Length]\t[Flow]");
		for(Map.Entry<String, Map<String, TestFlow>> kv : map.entrySet()) {
			System.out.printf("\t%d\t\t%s\n", kv.getValue().size(), kv.getKey());
		}
	}

	private static List<TestFlow> itemsOf(String name) {
		try {
			Field flow = flowClass.getDeclaredField(name);
			return Arrays.asList((TestFlow[])flow.get(null));
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static List<String> flowNames() {
		List<String> flows = new ArrayList<String>();
		for(Field fd : flowClass.getDeclaredFields()) {
			if(TestFlow[].class == fd.getType()) {
				flows.add(fd.getName());
			}
		}
		return flows;
	}
}

class Inject {
	final private static String targetFlow = "Debug";
	final private static Class<?> cls =  KDeviceTestProgram.class;
	private KDeviceTestProgram obj;

	private Inject(KDeviceTestProgram obj) {
		this.obj = obj;
	}

	final public static String getTargetFlow() {
		return new String(targetFlow);
	}

	final public static boolean isTargetFlow(String msg) {
		return targetFlow.equals(SS.testStep);
	}

	final public static Inject into(KDeviceTestProgram obj) {
		return new Inject(obj);
	}

	final public void update(TestItem ...tbs) {
		try {
			Field fd = cls.getDeclaredField("testItemRegistry");
			fd.setAccessible(true);
			KTestItemRegistry registry = (KTestItemRegistry)fd.get(obj);

			Method md = cls.getDeclaredMethod("registerTestItem", KTestItemRegistry.class, String.class, KTestItem[].class);
			md.setAccessible(true);
			md.invoke(obj, registry, Inject.getTargetFlow(), tbs);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}

class Signal {
	final private static String defaultSysVar = "ECOTS_SD_QUEUE";

	final public static String poll(String... vararr) {
		String sysVar = vararr.length == 0 ? defaultSysVar : vararr[0];
		System.out.printf("\n\nWaiting message from %s\n", sysVar);
		String msg;
		while((msg = KSystemVariable.read(sysVar)).isEmpty()) {
			try { 
				Thread.sleep(100);
			} catch(InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		return msg;
	}

	final public static void reset(String... vararr) {
		String sysVar = vararr.length == 0 ? defaultSysVar : vararr[0];
		KSystemVariable.write(sysVar, "");
	}

	final public static void lazyReset(String... vararr) {// avoids slow sites missing error message
		try { 
			Thread.sleep(100);
		} catch(InterruptedException e) {
			throw new RuntimeException(e);
		}
		reset(vararr);
	}
}


