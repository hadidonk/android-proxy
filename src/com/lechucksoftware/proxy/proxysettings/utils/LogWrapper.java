package com.lechucksoftware.proxy.proxysettings.utils;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogWrapper
{
	/**
	 *  ANDROID LOG LEVELS
	 * 
	 * 	VERBOSE	Constant Value: 2 (0x00000002)
	 *	DEBUG	Constant Value: 3 (0x00000003)
	 *	INFO	Constant Value: 4 (0x00000004)
	 *	WARN	Constant Value: 5 (0x00000005)
	 *	ERROR	Constant Value: 6 (0x00000006)
	 *	ASSERT	Constant Value: 7 (0x00000007)
	 * 
	 * */
	
//	private static int mLogLevel = Integer.MAX_VALUE;
	private static int mLogLevel = Log.DEBUG;
    private static Map<String,Date> startTraces;

	public static void d(String tag, String msg)
	{
		if (mLogLevel <= Log.DEBUG)
			Log.d(tag, msg);
	}
	
	public static void v(String tag, String msg)
	{
		if (mLogLevel <= Log.VERBOSE)
			Log.v(tag, msg);
	}
	
	public static void e(String tag, String msg)
	{
		if (mLogLevel <= Log.ERROR)
			Log.e(tag, msg);
	}

	public static void i(String tag, String msg)
	{
		if (mLogLevel <= Log.INFO)
			Log.i(tag, msg);
	}

    public static void w(String tag, String msg)
    {
        if (mLogLevel <= Log.WARN)
            Log.w(tag, msg);
    }
	
	public static void a(String tag, String msg)
	{
		if (mLogLevel <= Log.ASSERT)
			Log.println(Log.ASSERT, tag, msg);
	}

	public static void log(String tag, String msg, int logLevel)
	{
		switch(logLevel)
		{
			case Log.DEBUG:
				d(tag,msg);
				break;
			case Log.ERROR:
				e(tag,msg);
				break;
			case Log.INFO:
				i(tag,msg);
				break;
			case Log.ASSERT:
				a(tag,msg);
				break;
            case Log.WARN:
                w(tag,msg);
                break;
            default:
                v(tag,msg);
                break;
		}
	}

    public static void trace(String tag, String msg, int logLevel)
    {
        log(tag,msg + " ################## " + new Date().toLocaleString() + " #####################################################################",logLevel);
    }

    public static void startTrace(String tag, String msg, int logLevel)
    {
        if (startTraces == null)
        {
            startTraces = new ConcurrentHashMap<String, Date>();
        }

        Date now = new Date();
        log(tag,"START " + msg + " ################## " + now.toLocaleString() + " #####################################################################",logLevel);
        startTraces.put(msg,now);
    }


    public static void stopTrace(String tag, String msg, int logLevel)
    {
        if (startTraces != null && startTraces.containsKey(msg))
        {
            Date start = startTraces.get(msg);
            Date now = new Date();
            Long diff = now.getTime() - start.getTime();
            log(tag,"STOP " + msg + " ################## " + diff + " msec #####################################################################",logLevel);
        }
        else
        {
            log(tag,msg + " ################## " + new Date().toLocaleString() + " #####################################################################",logLevel);
        }
    }
	
	public static void logIntent(String tag, Intent intent, int logLevel)
	{
		logIntent(tag,intent,logLevel, false);
	}
	
	public static void logIntent(String tag, Intent intent, int logLevel, boolean logExtras)
	{
		log(tag, "LOG Intent: " + intent.toString(), logLevel);
    	if (intent.getAction() != null) log(tag, intent.getAction(), logLevel);
    	if (intent.getDataString() != null) log(tag, intent.getDataString(), logLevel);
    	
    	if (logExtras)
    	{
	    	Bundle extras = intent.getExtras();
	    	if (extras != null)	
	    	{
	        	for(String key: extras.keySet())
	        	{
	        		String extra = String.valueOf(extras.get(key));
	        		log(tag, "EXTRA [\"" + key + "\"] : " + extra, logLevel);
	        	}
	    	}
    	}
	}


}
