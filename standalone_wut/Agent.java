package wut;

//import javaapi.TestFlow;
//import javaapi.Seq_Test_Flow;
//import javaapi.TestItemList;
//import javaapi.DeviceInfo;
//import javaapi.EndOfFlow;
//import javaapi.TestItem;
//import javaapi.SS;
//import javaapi.NoSuchConditionException;
//import javaapi.NoMoreTargetDutException;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Array;

import com.advantest.kei.KSystemVariable;
import com.advantest.kei.KDeviceTestProgram;
import com.advantest.kei.KTestItemRegistry;
import com.advantest.kei.KTestItem;
import com.advantest.kei.IKNoMoreTargetDutAction;
import com.advantest.kei.KVariableRegistry;
import com.advantest.kei.KVariableRegistry;
import com.advantest.kei.KNoMoreTargetDutException;
import com.advantest.kei.KSystemVariable;

import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URL;
public class Agent extends KDeviceTestProgram implements IKNoMoreTargetDutAction {
	final private String targetFlow = "Debug";
	private KDeviceTestProgram currentTp;
	private TpClassLoader tpLoader;

	@Override public void initializeVariableRegistry(KVariableRegistry arg0) {
		this.tpLoader = TpClassLoader.from(getPath());
		this.currentTp = (KDeviceTestProgram)newInstance(getClassName());
		this.currentTp.initializeVariableRegistry(arg0);
	}

	@Override public void initializeTestItemRegistry(KTestItemRegistry registry) {
		this.currentTp.initializeTestItemRegistry(registry);
	}

	@Override public void testStartAction() throws KNoMoreTargetDutException {
		this.currentTp.testStartTime = System.currentTimeMillis();
		this.currentTp.testStartAction();
		logEnable(false);

		setFlowName(targetFlow);

		CacheTB.use(this.tpLoader);
		for(;;) {
			try {
				String msg = Signal.poll();
				Inject.into(this)
					.update(targetFlow,
							CacheTB.queryTestItems(
								CacheTB.parse(msg)));
				break;
			} catch(Exception e) {
				System.out.println("\n\n" + e);
				Signal.lazyReset();
			}
		}
		logEnable(true);
	}

	@Override public void programStartAction() {
		this.currentTp.programStartAction();
	}

	@Override public void testEndAction() throws Exception {
		this.currentTp.testEndAction();
	}

	@Override public void noMoreTargetDutAction() {
		((IKNoMoreTargetDutAction)this.currentTp).noMoreTargetDutAction();
	}

	private void logEnable(boolean enabled) {
		Class<?> devInfoCls = this.tpLoader.load("javaapi.DeviceInfo");
		TpVisitor.setField(devInfoCls, "datalogOutput", enabled);
	}

	private void setTestStartTime() {
		this.currentTp.testStartTime = System.currentTimeMillis();
	}

	private static String getPath() {
		return KSystemVariable.read("ECOTS_SD_TPPATH");
	}

	private static String getClassName() {
		return KSystemVariable.read("ECOTS_SD_TPCLASS")
			.replaceAll(".class", "")
			.replaceAll("/", "."); //like "javaapi.tea3628sh0401_192b"
	}

	private Object newInstance(String clsName) {
		try {
			Class<?> cls = this.tpLoader.load(clsName);
			return cls.newInstance();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
}

class TpVisitor {
	final public static void setField(Class<?> cls, String fdName, Object val) {
		try {
			Field fd = cls.getDeclaredField(fdName);
			fd.setAccessible(true);
			fd.set(null, val);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	final public static Field[] getFields(Class<?> cls) {
		try {
			return cls.getDeclaredFields();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	final public static Object getField(Class<?> cls, Object obj, String fdName) {
		try {
			Field fd = cls.getDeclaredField(fdName);
			fd.setAccessible(true);
			return fd.get(obj);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}




	final public static Method getMethod(Class<?> cls, String mdName, Class<?>... parameterTypes) {
		try {
			Method md = cls.getDeclaredMethod(mdName, parameterTypes);
			md.setAccessible(true);
			return md;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

}

class TpClassLoader {
	final private URLClassLoader urlClassLoader;

	private TpClassLoader(String path) {
		try {
			urlClassLoader = URLClassLoader.newInstance(new URL[] {
					new URL("file:///" + path.trim())	// "file:////home/kei/sandbox/users/wufeng/21144/T5773_BiCs3_2048Gb_X3_LGA70_S3E_8D1CE_tam3640af0980_768c/tam3640/"
					});
		} catch(MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	final public static TpClassLoader from(String path) {
		return new TpClassLoader(path);
	}

	final public Class<?> load(String className) {
		try {
			return urlClassLoader.loadClass(className);
		} catch(ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}

class FlowSearchOrder {
	final public static String[] values() {
		return arr;
	}
	private static String[] arr = {
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
}

class CacheTB {
	private static Class<?> flowCls; // = Seq_Test_Flow.class;
	private static Class<?> tilCls; // = TestItemList.class;
	private static Class<?> tfCls; // = TestFlow.class;

	private static Map<String, Map<String, Object>> map;
	private static Object til;

	final public static void use(TpClassLoader tpLoader) {
		if(map != null && til != null) {
			return;
		}

		loadClass(tpLoader);
		loadSeqTestFlow();
		loadTestItemList();
		stat();
	}

	final private static void loadClass(TpClassLoader tpLoader) {
		flowCls = tpLoader.load("javaapi.Seq_Test_Flow");
		tilCls = tpLoader.load("javaapi.TestItemList");
		tfCls = tpLoader.load("javaapi.TestFlow");
	}

	final private static void loadSeqTestFlow() {
		map = new HashMap<String, Map<String, Object>>();
		for(String flow : flowNames()) {
			Map<String, Object> inFlowMap = new HashMap<String, Object>();
			for(Object tf : itemsOf(flow)) {
				String key = (String)TpVisitor.getField(tfCls, tf, "testName");
				inFlowMap.put(key, tf);
			}
			map.put(flow, inFlowMap);
		}
	}

	final private static void loadTestItemList() {
		try {
			Constructor ctor = tilCls.getDeclaredConstructor();
			ctor.setAccessible(true);
			til = ctor.newInstance();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}


	final public static String[] parse(String msg) {
		return msg.trim().split(";");
	}

	final public static Object[] query(String... names) throws Exception {
		Object[] tfs = new Object[names.length];
		for(int i = 0; i < names.length; i++) {
			String name = names[i].trim();
			Object tf = searchInFlows(name);
			if(tf == null) {
				throw new Exception("Unknown " + name);
			}
			tfs[i] = tf;
		}
		return tfs;
	}

	final public static Object queryTestItems(String... names) throws Exception {
		Object[] tfs = query(names);
		Method md = TpVisitor.getMethod(tilCls, "getList", Class.forName("[Ljavaapi.TestFlow;"));

		System.out.println("after of invoke() " + md);//a8w4db
		return md.invoke(til, new Object[]{tfs});
	}

	private static Object searchInFlows(String name) throws Exception {
		for(String flow : FlowSearchOrder.values()) {
			if(! map.containsKey(flow)) {
				continue;
			}
			Object tf = map.get(flow).get(name);
			if(tf != null) {
				System.out.printf("%s: %s\n", TpVisitor.getField(tfCls, tf, "testName"), flow);
				printFlowItem(tf);
				return tf;
			}
		}
		return null;
	}

	final private static void printFlowItem(Object tf) {
		for(Field fd : TpVisitor.getFields(tfCls)) {
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

	final private static void stat() {
		System.out.println("\n\t[Length]\t[Flow]");
		for(Map.Entry<String, Map<String, Object>> kv : map.entrySet()) {
			System.out.printf("\t%d\t\t%s\n", kv.getValue().size(), kv.getKey());
		}
	}

	private static Object[] itemsOf(String name) {
		try {
			Field fd = flowCls.getDeclaredField(name);
			fd.setAccessible(true);
			Object arr = fd.get(null);
			Object[] tbs = new Object[Array.getLength(arr)];
			for(int i = 0; i < tbs.length; i += 1) {
				tbs[i] = Array.get(arr, i);
			}
			return tbs;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	final private static List<String> flowNames() {
		List<String> flows = new ArrayList<String>();
		for(Field fd : flowCls.getDeclaredFields()) {
			if(tfCls == fd.getType().getComponentType()) {
				flows.add(fd.getName());
			}
		}
		return flows;
	}
}

class Inject {
	final private static Class<?> cls =  KDeviceTestProgram.class;
	private Object obj;

	private Inject(KDeviceTestProgram obj) {
		this.obj = obj;
	}

	final public static Inject into(KDeviceTestProgram obj) {
		return new Inject(obj);
	}

	final public void update(String flow, Object ...tbs) {
		try {
			Field fd = cls.getDeclaredField("testItemRegistry");
			fd.setAccessible(true);
			KTestItemRegistry registry = (KTestItemRegistry)fd.get(obj);

			Method md = cls.getDeclaredMethod("registerTestItem", KTestItemRegistry.class, String.class, KTestItem[].class);
			md.setAccessible(true);
			md.invoke(obj, registry, flow, tbs);
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


