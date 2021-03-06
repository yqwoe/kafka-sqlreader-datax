package com.nascent.pipeline.processor.mysql;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrSubstitutor;
import com.alibaba.fastjson.JSONObject;
import com.nascent.pipeline.subscriber.xmltags.EventRoot;
import com.nascent.pipeline.subscriber.xmltags.TransMap;

public class MysqlProcessor {
	Properties config;
	public Map<String,String> IgnoreTables;
	public MysqlProcessor(){
		config = new Properties();
		try {
			InputStream in = new FileInputStream(System.getProperty("user.dir")+"/conf/mysql.properties");
			config.load(in);
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		//this.ds = connect();
		
		IgnoreTables=new HashMap<>();
		String ignoreTable = System.getProperty("binlog.ignoreTables",
							 config.getProperty("binlog.ignoreTables", ""));
		String[] ignores = ignoreTable.split(",");
		for(String table : ignores){
			String[] dbTable = StringUtils.split(table, ".");
			if(dbTable.length==2){
				if(!IgnoreTables.containsKey(dbTable[0]))
					IgnoreTables.put(dbTable[0],","+dbTable[1]+",");
				else{
					IgnoreTables.put(dbTable[0],IgnoreTables.get(dbTable[0])+dbTable[1]+",");
				}
			}
		}
	}
	public static String getBoundSql(String sql, JSONObject json){
		if(sql.indexOf("${Database}")<0)
			throw new RuntimeException("Sql without specified '${Database}' is not allowed! ");
		StrSubstitutor binder = new StrSubstitutor(EventRoot.getData(json));
		return binder.replace(sql);
	}
	
	/**
	 * 获取每个店铺当前同步进度ts_start
	 * @param database
	 * @return
	 */
	public String getTimestamp(String database){
		String sql = config.getProperty("jdbc.querySql")
						.replace("$ts_key", database);

		try (Connection conn = connect()){
			ResultSet rs =conn.createStatement()
					.executeQuery(sql);
			if(rs.next())
				return rs.getString(1);
		}catch(Exception ex){
	    	throw new RuntimeException(sql,ex);
	    }
		return null;
	}
	
	/**
	 * 结果集完全由sql决定，不再进行mapping
	 * 输出ArrayList of Map
	 * @param sql
	 * @param json
	 */
	public void process(String sql, JSONObject json){
		List<Map<String,Object>> li = executeQuery(getBoundSql(sql,json));
		json.put("Result",li);
	}
	
	protected List<Map<String,Object>> executeQuery(String sql){
		List<Map<String,Object>> li = new ArrayList<>();
		
	    try (Connection conn = connect()){
	        try (Statement stmt = conn.createStatement()) {
	          ResultSet rs = stmt.executeQuery(sql);
	          
	          ResultSetMetaData metaData = rs.getMetaData();
	          int columnNumber = metaData.getColumnCount();
	          
	          while(rs.next()){
		          Map<String,Object> record=new HashMap<>(columnNumber);
		          ResultSetReadProxy.transportOneRecord(record, rs, metaData, columnNumber, null);
		          
		          li.add(record);
	          }
	          
	          //stmt.close();
	      }
	      conn.commit();
	    }catch(Exception ex){
	    	throw new RuntimeException(sql,ex);
	    }
	    return li;
	  }
	
	private int executeUpdate(String sql){
		int count=0;
	    try (Connection conn = connect()) {
	        try (Statement stmt = conn.createStatement()) {
	          count = stmt.executeUpdate(sql);
	          stmt.close();
	      }
	      conn.close();
	    }
	    catch(Exception ex){
	    	throw new RuntimeException(sql,ex);
	    }
	    return count;
	  }
	
	
	protected Connection connect() throws Exception{
		Class.forName("com.mysql.jdbc.Driver");
		return DriverManager.getConnection(
				config.getProperty("jdbc.url"), 
				config.getProperty("jdbc.user"), 
				config.getProperty("jdbc.pwd"));
	    /*HikariConfig hikariConfig = new HikariConfig();
	    hikariConfig.setJdbcUrl(config.getProperty("jdbc.url"));
	    hikariConfig.setUsername(config.getProperty("jdbc.user"));
	    hikariConfig.setPassword(config.getProperty("jdbc.pwd"));
	    hikariConfig.addDataSourceProperty("useSSL", false);
	    hikariConfig.setAutoCommit(false);
	    return new HikariDataSource(hikariConfig);*/
	}
}
