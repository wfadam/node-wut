package wut;

import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.advantest.kei.KSystemVariable;
import com.advantest.kei.KDeviceTestProgram;
import com.advantest.kei.KTestItemRegistry;
import com.advantest.kei.KTestItem;
import com.advantest.kei.IKNoMoreTargetDutAction;
import com.advantest.kei.KVariableRegistry;
import com.advantest.kei.KNoMoreTargetDutException;

import java.net.URLClassLoader;
import java.net.MalformedURLException;
import java.net.URL;

public class Agent extends KDeviceTestProgram implements IKNoMoreTargetDutAction {
	final private String targetFlow = "Debug";
	private KDeviceTestProgram currentTp;
	private TpClassLoader tpLoader;

	@Override public void initializeVariableRegistry(KVariableRegistry arg0) {
		this.tpLoader = TpClassLoader.from(getTpPath());
		this.currentTp = (KDeviceTestProgram)newInstance(getTpClassName());
		this.currentTp.initializeVariableRegistry(arg0);
	}

	@Override public void initializeTestItemRegistry(KTestItemRegistry registry) {
		this.currentTp.initializeTestItemRegistry(registry);
	}

	@Override public void testStartAction() throws KNoMoreTargetDutException {
		this.currentTp.testStartAction();

		setTestStartTime();
		setFlowName(this.targetFlow);

		for(;;) {
			try {
				String msg = Signal.poll();
				KTestItem[] tbArr = getTestItemArr(getTbNames(msg));
				addFlow(this.targetFlow, tbArr);
				break;
			} catch(Exception e) {
				System.out.println("\n\n" + e);
				Signal.lazyReset();
			}
		}
	}

	@Override public void programStartAction() {
		this.currentTp.programStartAction();
	}

	@Override public void testEndAction() throws Exception {
		this.currentTp.testEndAction();
	}

	@Override public void noMoreTargetDutAction() {
		try {
			Method setCategory =  Ano.getMethod(tpLoader.load("javaapi.TestItem"), "setCategory");
			setCategory.invoke(getCurrentTestItem()); 

			Method body =  Ano.getMethod(tpLoader.load("javaapi.EndOfFlow"), "body");
			body.invoke(getTestItemArr("End Of Flow")[0]);
		} catch(Exception e) {
			System.out.println(e);
		}
	}

	private String[] getTbNames(String msg) {
		return msg.trim().split(";");
	}

	private void setTestStartTime() {
		this.currentTp.testStartTime = System.currentTimeMillis();
	}

	private String getTpPath() {
		return KSystemVariable.read("ECOTS_SD_TPPATH");
	}

	private String getTpClassName() {
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

	private Map<String, KTestItem[]> getTestItemArrayMap() {
		KTestItemRegistry tbReg = (KTestItemRegistry)Ano.fieldValue(
				KDeviceTestProgram.class, this, "testItemRegistry");
		return tbReg.getTestItemArrayMap();
	}

	private KTestItem search(Map<String, KTestItem[]> map, String name) {
		for(String flow : FlowSearchOrder.values()) {
			KTestItem[] tbArr = map.get(flow);
			if(tbArr == null) {
				continue;
			}

			for(KTestItem tb : tbArr) {
				if(name.equals(tb.getName())) {
					System.out.printf("%s: %s\n", flow, name);
					printTB(tb);
					return tb;
				}
			}
		}
		return null;
	}

	private void printTB(KTestItem tb) {
		Class<?> tbCls = this.tpLoader.load("javaapi.TestItem");
		System.out.printf("\tFailBin: %d\n", (Integer)Ano.fieldValue(tbCls, tb, "fail"));
		System.out.printf("\tIgnoreBit: %d\n", (Integer)Ano.fieldValue(tbCls, tb, "ignorebit"));
		System.out.printf("\tPage: 0x%X\n", (Integer)Ano.fieldValue(tbCls, tb, "page"));
		System.out.printf("\tCol: 0x%X\n", (Integer)Ano.fieldValue(tbCls, tb, "col"));
	}

	private KTestItem[] getTestItemArr(String... tbNames) throws Exception {
		Map<String, KTestItem[]> map = getTestItemArrayMap();
		KTestItem[] tbArr = new KTestItem[tbNames.length];
		for(int i = 0; i < tbArr.length; i += 1) {
			tbArr[i] = search(map, tbNames[i]);
			if(tbArr[i] == null) {
				throw new Exception("Unknown " + tbNames[i]);
			}
		}
		return tbArr;
	}

	final public void addFlow(String flow, KTestItem[] tbArr) {
		Object fd = Ano.fieldValue(KDeviceTestProgram.class, this, "testItemRegistry");
		registerTestItem((KTestItemRegistry)fd, flow, tbArr);
	}

}


class Ano {
	final public static void setField(Class<?> cls, Object obj, String fdName, Object val) {
		try {
			Field fd = cls.getDeclaredField(fdName);
			fd.setAccessible(true);
			fd.set(obj, val);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	final public static Object fieldValue(Class<?> cls, Object obj, String fdName) {
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
					new URL("file:///" + path.trim())	// "file:////home/kei/sandbox//tam4567/"
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
		"FH",
		"FR",
		"FL",
		"SH",
		"CFH",
		"CSH",
		"QH",
		"QR",
		"QL",
		"CH",
		"CR",
		"CL",
		"Debug",
		"DebugPats",
	};
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


