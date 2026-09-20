package com.store.app;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.webkit.WebView;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class RoyalPanopticon {
    public static final String TAG="[ROYAL_DIAGNOSTICS]";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Map<String,EngineRecord> ENGINES=new ConcurrentHashMap<>();
    private static final Map<String,Set<String>> DEPS=new ConcurrentHashMap<>();
    private static final List<Diagnostic> ERRORS=new CopyOnWriteArrayList<>();
    private static final AtomicLong MAX_FREEZE=new AtomicLong(),TOTAL_FREEZE=new AtomicLong(),FREEZE_COUNT=new AtomicLong();
    private static ScheduledExecutorService EXEC;
    private static Context context;
    private static volatile boolean running;
    private static volatile long lastHeartbeat=System.currentTimeMillis(),lastAnalysis,lastHeap,peakHeap;

    private static final BrowserState BROWSER=new BrowserState();
    private static final NavigationMetric NAV=new NavigationMetric();
    private static final List<Anomaly> ANOMALIES=new CopyOnWriteArrayList<>();
    private static final Thread.UncaughtExceptionHandler ORIGINAL=Thread.getDefaultUncaughtExceptionHandler();

    private RoyalPanopticon(){}

    private static final class BrowserState {
        volatile int dom,fps=60,longTasks;
        volatile long memory,lastUpdate=System.currentTimeMillis();
        final long[] fpsH=new long[12],domH=new long[12],memH=new long[12];
        int fi,di,mi,fc,dc,mc;
        synchronized void update(int d,int f,long m,int l){
            dom=d;fps=f;memory=m;longTasks=l;lastUpdate=System.currentTimeMillis();
            fpsH[fi]=f;fi=(fi+1)%12;if(fc<12)fc++;
            domH[di]=d;di=(di+1)%12;if(dc<12)dc++;
            memH[mi]=m;mi=(mi+1)%12;if(mc<12)mc++;
        }
        synchronized long avg(long[] a,int n,int c){
            if(c<=0)return 0;long s=0;for(int i=0;i<c;i++)s+=a[i];return s/c;
        }
        synchronized long min(long[] a,int c){
            if(c<=0)return 0;long x=Long.MAX_VALUE;for(int i=0;i<c;i++)x=Math.min(x,a[i]);return x;
        }
    }

    public static final class NavigationMetric {
        public String url="";
        public long clickTimestamp,requestSentTimestamp,firstByteTimestamp,domInteractiveTimestamp,domCompleteTimestamp,uiThreadBlockMs;
        synchronized void reset(String u){
            url=u==null?"":u;
            clickTimestamp=System.currentTimeMillis();
            requestSentTimestamp=firstByteTimestamp=domInteractiveTimestamp=domCompleteTimestamp=uiThreadBlockMs=0;
        }
    }

    private static final class EngineRecord {
        final String name;
        final AtomicLong ops=new AtomicLong(),failures=new AtomicLong(),latency=new AtomicLong(),peakMemory=new AtomicLong();
        volatile long lastPulse=System.currentTimeMillis();
        EngineRecord(String n){name=n;}
        double avg(){long o=ops.get();return o==0?0:latency.get()/(double)o;}
        double health(){long o=ops.get();return o==0?100:Math.max(0,100-failures.get()*100.0/o);}
    }

    private static final class Diagnostic {
        final long time=System.currentTimeMillis();
        final String type,area,message,source,stack;
        Diagnostic(String t,String a,String m,String s,String st){type=t;area=a;message=m;source=s;stack=st;}
    }

    private static final class Anomaly {
        final String area,level,reason,evidence;
        Anomaly(String a,String l,String r,String e){area=a;level=l;reason=r;evidence=e;}
    }

    public static synchronized void startAwareness(){
        if(running)return;
        running=true;
        context=tryContext();

        Thread.setDefaultUncaughtExceptionHandler((t,e)->{
            reportError("UNCAUGHT",e,"Fatal exception on thread "+t.getName());
            if(ORIGINAL!=null)try{ORIGINAL.uncaughtException(t,e);}catch(Throwable ignored){}
        });

        EXEC=Executors.newSingleThreadScheduledExecutor(r->{
            Thread t=new Thread(r,"Panopticon-AI");t.setDaemon(true);return t;
        });
        EXEC.scheduleAtFixedRate(RoyalPanopticon::cycle,1,2,TimeUnit.SECONDS);

        log("PANOPTICON V6 ONLINE");
        log("ANDROID API="+Build.VERSION.SDK_INT+" RELEASE="+Build.VERSION.RELEASE);
        log("PROCESS PID="+Process.myPid());
        logWebViewProvider();
    }

    public static synchronized void stopAwareness(){
        running=false;
        if(EXEC!=null){EXEC.shutdownNow();EXEC=null;}
        log("PANOPTICON V6 OFFLINE");
    }

    private static void cycle(){
        if(!running)return;
        final long posted=System.nanoTime();
        MAIN.post(()->{
            long lag=(System.nanoTime()-posted)/1_000_000L;
            lastHeartbeat=System.currentTimeMillis();
            if(lag>=32){
                FREEZE_COUNT.incrementAndGet();
                TOTAL_FREEZE.addAndGet(lag);
                MAX_FREEZE.accumulateAndGet(lag,Math::max);
                synchronized(NAV){if(NAV.clickTimestamp>0&&NAV.domCompleteTimestamp==0)NAV.uiThreadBlockMs+=lag;}
                if(lag>=100)diagnose("MAIN_THREAD","UI thread blocked "+lag+"ms",caller());
            }
        });
        analyze();
    }

    private static void analyze(){
        ANOMALIES.clear();
        long now=System.currentTimeMillis(),silence=now-BROWSER.lastUpdate;
        lastHeap=Debug.getPss()/1024L;peakHeap=Math.max(peakHeap,lastHeap);

        if(lastHeap>256)addAnomaly("MEMORY","HIGH","High application memory pressure","PSS="+lastHeap+"MB peak="+peakHeap+"MB");
        long freeze=MAX_FREEZE.get();
        if(freeze>=250)addAnomaly("MAIN_THREAD","CRITICAL","Severe UI scheduling delay","maxFreeze="+freeze+"ms count="+FREEZE_COUNT.get());
        else if(freeze>=100)addAnomaly("MAIN_THREAD","HIGH","Noticeable UI blocking","maxFreeze="+freeze+"ms");

        synchronized(BROWSER){
            if(BROWSER.fps<45)addAnomaly("WEBVIEW_RENDER","HIGH","Low reported WebView FPS","fps="+BROWSER.fps+" avg="+BROWSER.avg(BROWSER.fpsH,0,BROWSER.fc));
            if(BROWSER.longTasks>0)addAnomaly("JAVASCRIPT","MEDIUM","JavaScript long tasks detected","longTasks="+BROWSER.longTasks);
            if(BROWSER.dom>2000)addAnomaly("DOM","MEDIUM","Large DOM tree","nodes="+BROWSER.dom);
            if(BROWSER.fps<50&&BROWSER.longTasks>2)addAnomaly("RENDERING","HIGH","Low FPS correlated with JS long tasks","fps="+BROWSER.fps+" tasks="+BROWSER.longTasks);
            if(BROWSER.dom>0&&silence>15000)addAnomaly("WEBVIEW_BRIDGE","HIGH","Browser telemetry heartbeat stopped","silence="+silence+"ms dom="+BROWSER.dom);
        }

        analyzeNavigation();
        analyzeEngines();
        analyzeThreads();
        lastAnalysis=now;
    }

    private static void analyzeNavigation(){
        synchronized(NAV){
            if(NAV.clickTimestamp==0||NAV.domCompleteTimestamp==0)return;
            long total=diff(NAV.domCompleteTimestamp,NAV.clickTimestamp);
            long bridge=diff(NAV.requestSentTimestamp,NAV.clickTimestamp);
            long server=diff(NAV.firstByteTimestamp,NAV.requestSentTimestamp);
            long render=diff(NAV.domCompleteTimestamp,NAV.firstByteTimestamp);

            if(total>=3000){
                if(server>total*.60)addAnomaly("NETWORK","HIGH","Server/network dominates navigation","total="+total+"ms server="+server+"ms");
                else if(render>total*.60)addAnomaly("WEBVIEW_RENDER","HIGH","Client rendering dominates navigation","total="+total+"ms render="+render+"ms");
                else if(bridge>500)addAnomaly("BRIDGE","HIGH","Navigation bridge delay is high","bridge="+bridge+"ms");
            }
        }
    }

    private static void analyzeEngines(){
        for(EngineRecord r:ENGINES.values()){
            long o=r.ops.get();if(o==0)continue;
            if(r.failures.get()>0)addAnomaly(r.name,"ERROR","Engine reported failures","ops="+o+" failures="+r.failures.get());
            if(r.avg()>=100)addAnomaly(r.name,"HIGH","High average execution latency","avg="+fmt(r.avg())+"ms");
        }
    }

    private static void analyzeThreads(){
        if(context==null)return;
        try{
            ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(am==null)return;
            List<ActivityManager.RunningAppProcessInfo> ps=am.getRunningAppProcesses();
            if(ps==null)return;
            int own=0;StringBuilder s=new StringBuilder();
            for(ActivityManager.RunningAppProcessInfo p:ps)
                if(p.pid==Process.myPid()||(p.processName!=null&&p.processName.startsWith(context.getPackageName()))){
                    own++;if(s.length()<500)s.append(p.processName).append(' ');
                }
            if(own>8)addAnomaly("PROCESS","MEDIUM","Multiple application processes detected","count="+own+" "+s);
        }catch(Throwable e){reportError("PROCESS_MONITOR",e,"Failed reading application processes");}
    }

    private static void logWebViewProvider(){
        try{
            if(Build.VERSION.SDK_INT>=26){
                android.content.pm.PackageInfo p=WebView.getCurrentWebViewPackage();
                if(p!=null)log("WEBVIEW_PROVIDER "+p.packageName+" version="+p.versionName);
            }
        }catch(Throwable e){reportError("WEBVIEW_PROVIDER",e,"Unable to read WebView provider");}
    }

    public static void syncBrowserState(int nodes,int fps,long memory,int tasks){BROWSER.update(nodes,fps,memory,tasks);}

    public static void recordMetric(String name,long value){
        if(name==null)return;
        String src=caller();
        log("METRIC "+name+"="+value+"ms @ "+src);
        if(value>=3000)diagnose("PERFORMANCE","Slow "+name+"="+value+"ms",src);
    }

    public static void recordUserClick(String url){synchronized(NAV){NAV.reset(url);}pulse("Navigation");}

    public static void recordRequestSent(){synchronized(NAV){if(NAV.clickTimestamp>0)NAV.requestSentTimestamp=System.currentTimeMillis();}}

    public static void recordFirstByteReceived(){synchronized(NAV){if(NAV.requestSentTimestamp>0)NAV.firstByteTimestamp=System.currentTimeMillis();}}

    public static void recordDomInteractive(){synchronized(NAV){if(NAV.clickTimestamp>0)NAV.domInteractiveTimestamp=System.currentTimeMillis();}}

    public static void recordNavigationComplete(){
        synchronized(NAV){
            if(NAV.clickTimestamp>0){
                NAV.domCompleteTimestamp=System.currentTimeMillis();
                printTransition();
            }
        }
    }

    public static void pulse(String name){if(name!=null)get(name).lastPulse=System.currentTimeMillis();}

    public static void recordExecution(String name,long latencyMs,boolean success,long memoryBytes){
        if(name==null)return;
        EngineRecord r=get(name);
        r.ops.incrementAndGet();
        r.latency.addAndGet(Math.max(0,latencyMs));
        if(!success){
            r.failures.incrementAndGet();
            diagnose("ENGINE_ERROR",name+" reported unsuccessful execution",caller());
        }
        r.peakMemory.accumulateAndGet(Math.max(0,memoryBytes),Math::max);
        if(latencyMs>=500)diagnose("ENGINE_SLOW",name+" execution took "+latencyMs+"ms",caller());
    }

    public static void registerDependency(String parent,String child){
        if(parent==null||child==null)return;
        DEPS.computeIfAbsent(parent,k->ConcurrentHashMap.newKeySet()).add(child);
        get(parent);get(child);
    }

    private static EngineRecord get(String n){return ENGINES.computeIfAbsent(n,EngineRecord::new);}

    public static void recordError(String area,String message){
        diagnose(area,message,caller());
    }

    public static void reportError(String area,Throwable t){
        reportError(area,t,"Exception reported");
    }

    public static void reportError(String area,Throwable t,String message){
        if(t==null){diagnose(area,message,caller());return;}
        String src=sourceFromThrowable(t);
        String stack=Log.getStackTraceString(t);
        String reason=reason(t);
        Diagnostic d=new Diagnostic("EXCEPTION",area,message+" | "+reason,src,stack);
        ERRORS.add(d);
        Log.e(TAG,"╔══ DIAGNOSTIC ERROR ══");
        Log.e(TAG,"AREA="+area);
        Log.e(TAG,"MESSAGE="+message);
        Log.e(TAG,"TYPE="+t.getClass().getName());
        Log.e(TAG,"REASON="+reason);
        Log.e(TAG,"SOURCE="+src);
        Log.e(TAG,"STACK\n"+stack);
        Log.e(TAG,"╚══════════════════════");
    }

    public static void reportWebError(String source,int line,String message,String sourceId){
        String s=(source==null?"unknown":source)+":"+line;
        diagnose("WEBVIEW_JS","JS error: "+message+" | resource="+sourceId,s);
    }

    public static void reportConsoleError(String message,String source,int line){
        diagnose("WEBVIEW_CONSOLE","Console error: "+message,source+":"+line);
    }

    private static void diagnose(String area,String message,String source){
        Diagnostic d=new Diagnostic("DIAGNOSTIC",area,message,source,"");
        ERRORS.add(d);
        Log.e(TAG,"╔══ ROYAL DIAGNOSTIC ══");
        Log.e(TAG,"AREA="+area);
        Log.e(TAG,"DETAIL="+message);
        Log.e(TAG,"SOURCE="+source);
        Log.e(TAG,"╚══════════════════════");
    }

    private static String reason(Throwable t){
        if(t instanceof NullPointerException)return "Likely null object/reference access; inspect SOURCE and first project stack frame.";
        if(t instanceof IllegalStateException)return "Invalid component state or lifecycle order.";
        if(t instanceof IllegalArgumentException)return "Invalid argument/value passed to an API.";
        if(t instanceof SecurityException)return "Permission, URI, WebView security policy, or restricted API operation.";
        if(t instanceof OutOfMemoryError)return "Memory exhaustion; inspect PSS, allocations and large WebView resources.";
        if(t instanceof java.net.UnknownHostException)return "DNS/host resolution failure or network unavailable.";
        if(t instanceof java.net.SocketTimeoutException)return "Network/server response timeout.";
        if(t instanceof java.io.IOException)return "I/O or network operation failed.";
        return "Inspect the first application-owned stack frame for the originating failure.";
    }

    private static String sourceFromThrowable(Throwable t){
        for(StackTraceElement e:t.getStackTrace())
            if(!e.getClassName().equals(RoyalPanopticon.class.getName())&&e.getClassName().startsWith("com.store.app"))
                return e.getFileName()+":"+e.getLineNumber()+" -> "+e.getMethodName()+"()";
        StackTraceElement[] s=t.getStackTrace();
        return s.length>0?s[0].toString():"unknown";
    }

    private static String caller(){
        for(StackTraceElement e:Thread.currentThread().getStackTrace()){
            String c=e.getClassName();
            if(c.startsWith("com.store.app")&&!c.equals(RoyalPanopticon.class.getName()))
                return e.getFileName()+":"+e.getLineNumber()+" -> "+e.getMethodName()+"()";
        }
        return "unknown";
    }

    public static void printFullDiagnosticsReport(){Log.i(TAG,buildReport());}

    public static String buildReport(){
        analyze();
        StringBuilder s=new StringBuilder(8192);
        s.append("\n=== ROYAL PANOPTICON V6 ===\n");
        s.append("SYSTEM API=").append(Build.VERSION.SDK_INT).append(" Android=").append(Build.VERSION.RELEASE).append(" PID=").append(Process.myPid()).append('\n');
        s.append("MEMORY PSS=").append(lastHeap).append("MB PEAK=").append(peakHeap).append("MB\n");
        s.append("UI FREEZE=").append(MAX_FREEZE.get()).append("ms COUNT=").append(FREEZE_COUNT.get()).append('\n');
        s.append("WEBVIEW FPS=").append(BROWSER.fps).append(" AVG=").append(BROWSER.avg(BROWSER.fpsH,0,BROWSER.fc)).append(" MIN=").append(BROWSER.min(BROWSER.fpsH,BROWSER.fc)).append(" DOM=").append(BROWSER.dom).append(" JSHEAP=").append(BROWSER.memory).append("MB LONGTASKS=").append(BROWSER.longTasks).append('\n');

        synchronized(NAV){
            if(NAV.clickTimestamp>0){
                long total=diff(NAV.domCompleteTimestamp,NAV.clickTimestamp),bridge=diff(NAV.requestSentTimestamp,NAV.clickTimestamp),server=diff(NAV.firstByteTimestamp,NAV.requestSentTimestamp),render=diff(NAV.domCompleteTimestamp,NAV.firstByteTimestamp);
                s.append("NAV URL=").append(NAV.url).append(" TOTAL=").append(total).append("ms BRIDGE=").append(bridge).append("ms SERVER=").append(server).append("ms RENDER=").append(render).append("ms UIBLOCK=").append(NAV.uiThreadBlockMs).append("ms\n");
            }
        }

        s.append("ENGINES\n");
        for(EngineRecord r:ENGINES.values())
            s.append(r.name).append(" ops=").append(r.ops.get()).append(" fail=").append(r.failures.get()).append(" avg=").append(fmt(r.avg())).append("ms health=").append(fmt(r.health())).append("%\n");

        s.append("ANOMALIES=").append(ANOMALIES.size()).append('\n');
        for(Anomaly a:ANOMALIES)s.append("! ").append(a.level).append(" | ").append(a.area).append(" | ").append(a.reason).append(" | ").append(a.evidence).append('\n');

        s.append("ERRORS=").append(ERRORS.size()).append('\n');
        int start=Math.max(0,ERRORS.size()-10);
        for(int i=start;i<ERRORS.size();i++){
            Diagnostic d=ERRORS.get(i);
            s.append("# ").append(d.type).append(" | ").append(d.area).append(" | ").append(d.message).append(" | ").append(d.source).append('\n');
            if(!d.stack.isEmpty())s.append(d.stack).append('\n');
        }
        s.append("=== END V6 ===\n");
        return s.toString();
    }

    private static void addAnomaly(String a,String l,String r,String e){ANOMALIES.add(new Anomaly(a,l,r,e));}

    private static long diff(long a,long b){return a>0&&b>0&&a>=b?a-b:0;}

    private static String fmt(double n){return String.format(Locale.US,"%.1f",n);}

    private static void printTransition(){
        synchronized(NAV){
            long total=diff(NAV.domCompleteTimestamp,NAV.clickTimestamp),bridge=diff(NAV.requestSentTimestamp,NAV.clickTimestamp),server=diff(NAV.firstByteTimestamp,NAV.requestSentTimestamp),render=diff(NAV.domCompleteTimestamp,NAV.firstByteTimestamp);
            log("NAV total="+total+"ms bridge="+bridge+"ms server="+server+"ms render="+render+"ms ui="+NAV.uiThreadBlockMs+"ms");
        }
    }

    private static void log(String s){Log.i(TAG,s);}

    private static Context tryContext(){
        try{return (Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);}
        catch(Throwable ignored){return null;}
    }
}
