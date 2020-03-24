package com.ucar.datalink.biz.utils;

import com.alibaba.fastjson.JSONObject;
import com.ucar.datalink.biz.dal.MediaSourceDAO;
import com.ucar.datalink.biz.meta.MetaManager;
import com.ucar.datalink.biz.meta.MetaMapping;
import com.ucar.datalink.biz.service.JobService;
import com.ucar.datalink.biz.utils.flinker.FlinkerJobConfigConstant;
import com.ucar.datalink.biz.utils.flinker.job.JobConfigBuilder;
import com.ucar.datalink.common.errors.DynamicParamException;
import com.ucar.datalink.common.utils.DLConfig;
import com.ucar.datalink.domain.job.FlinkerMachineInfo;
import com.ucar.datalink.domain.job.JobConfigInfo;
import com.ucar.datalink.domain.job.JobExecutionInfo;
import com.ucar.datalink.domain.job.JobRunningData;
import com.ucar.datalink.domain.media.MediaSourceInfo;
import com.ucar.datalink.domain.media.MediaSourceType;
import com.ucar.datalink.domain.meta.ColumnMeta;
import com.ucar.datalink.domain.meta.MediaMeta;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkMarshallingError;
import org.I0Itec.zkclient.serialize.ZkSerializer;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by lubiao on 2017/8/17.
 */
public class DataxUtil {

    private static final Logger logger = LoggerFactory.getLogger(DataxUtil.class);

    /**
     * datax 在zookeeper上注册的机器ip
     */
    private static final String DATAX_ADMIN_WORKERS_PATH = "/datax/admin/workers";

    /**
     * datax 在zookeeper上运行的任务
     */
    private static final String DATAX_ADMIN_JOBS_RUNNING = "/datax/admin/jobs/running";

    /**
     * 启动datax进程的rest url
     */
    private static final String START = "/admin/start";

    /**
     * 停止datax进程的rest url
     */
    private static final String STOP = "/admin/stop";

    /**
     * 强制停止datax进程的rest url
     */
    private static final String FORCE_STOP = "/admin/forcestop";

    /**
     * 获取一个datax admin机器系统信息的rest url
     */
    private static final String MACHINE_STATE = "/admin/state";


    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private static final String TIME_FORMAT = "HH-";

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";


    /**
     * datax启动的rest服务的默认端口
     */
    private static final String DATAX_DEFAULT_REST_PORT = "8083";

    /**
     * 一天的总时间（毫秒）
     */
    private static final long ONE_DAY_TIME_BY_MILLISECOND = 24 * 3600 * 1000;

    private static ZkClient client;

    static {
        String servers = ConfigReadUtil.getString("datax.zookeeper.servers");
        int sessionTimeout = ConfigReadUtil.getInt("datax.zookeeper.session.timeout.ms");
        int connectTimeout = ConfigReadUtil.getInt("datax.zookeeper.connection.timeout.ms");
        client = new ZkClient(servers, sessionTimeout, connectTimeout);
        client.setZkSerializer(new ZkSerializer(){
            @Override
            public byte[] serialize(Object data) throws ZkMarshallingError {
                try {
                    return ((String) data).getBytes("utf-8");
                } catch (final UnsupportedEncodingException e) {
                    throw new ZkMarshallingError(e);
                }
            }

            @Override
            public Object deserialize(byte[] bytes) throws ZkMarshallingError {
                try {
                    return new String(bytes, "utf-8");
                } catch (final UnsupportedEncodingException e) {
                    throw new ZkMarshallingError(e);
                }
            }
        });
    }


    /**
     * 根据job配置的id，获取最近一次执行的历史信息，相关查询的sql如下
     *       SELECT * FROM `t_datax_job_execution` WHERE job_id=#{0} ORDER BY start_time DESC LIMIT 1;
     * @param id
     * @return
     */
    private static JobExecutionInfo getLastSuccessExecuteJobExecutionInfo(long id) {
        JobService serivce = DataLinkFactory.getObject(JobService.class);
        return serivce.lastSuccessExecuteJobExecutionInfo(id);
    }

    private static MediaSourceInfo getMediaSourceInfo(long mediaSourceid) {
        MediaSourceDAO dao = DataLinkFactory.getObject(MediaSourceDAO.class);
        return dao.getById(mediaSourceid);
    }

    /**
     * 查询zookeeper的/datax/admin/workers 节点，获取所有正在运行的datax admin机器列表
     * @return
     */
    public static List<String> getDataxMachineAddress() {
        List<String> list = client.getChildren(DATAX_ADMIN_WORKERS_PATH);
        return list;
    }

    /**
     * 随机获取一个datax机器的地址  ip和port组成的地址
     * @return
     */
    public static String getWorkAddressByRandom() {
        List<String> list = getDataxMachineAddress();
        Random rand = new Random();
        if(list==null || list.size()==0) {
            throw new RuntimeException("list is emtpy");
        }
        int index = Math.abs( rand.nextInt(list.size()) );
        String ip = list.get(index);
        String address_and_port = "http://"+ ip +":"+ DATAX_DEFAULT_REST_PORT;
        return address_and_port;
    }


    /**
     * 根据ip地址加上默认端口，拼接一个地址
     * @param ip
     * @return
     */
    public static String assembleAddress(String ip) {
        String address_and_port = "http://"+ ip +":"+ DATAX_DEFAULT_REST_PORT;
        return address_and_port;
    }

    /**
     * 根据ip拼接一个启动datax服务的url
     * @param ip
     * @return
     */
    public static String startURL(String ip) {
        return "http://"+ ip +":"+DATAX_DEFAULT_REST_PORT + START;
    }

    /**
     * 根据ip拼接一个停止datax服务的url
     * @param ip
     * @return
     */
    public static String stopURL(String ip) {
        return "http://"+ ip +":"+DATAX_DEFAULT_REST_PORT + STOP;
    }

    /**
     * 根据ip拼接一个强制停止datax服务的url
     * @param ip
     * @return
     */
    public static String forceStopURL(String ip) {
        return "http://"+ ip +":"+DATAX_DEFAULT_REST_PORT + FORCE_STOP;
    }


    /**
     * 获取一个启动datax服务的url，url中的datax机器ip是随机获取的
     * @return
     */
    public static String startURL() {
        return getWorkAddressByRandom() + START;
    }

    /**
     * 获取一个关闭datax服务的url，url中的datax机器ip是随机获取的
     * @return
     */
    public static String stopURL() {
        return getWorkAddressByRandom() + STOP;
    }

    /**
     * 获取一个强制关闭datax服务的url，url中的datax机器ip是随机获取的
     * @return
     */
    public static String forceStopURL() {
        return getWorkAddressByRandom() + FORCE_STOP;
    }


    /**
     * 获取所有运行的job列表
     * @return
     */
    public static Set<String> getDataxRunningTask() {
        List<String> list = client.getChildren(DATAX_ADMIN_JOBS_RUNNING);
        if(list == null) {
            return new HashSet<>();
        }
        return new HashSet<>(list);
    }

    /**
     * 从zookeeper中获取指定job的内容，这是从 /datax/admin/jobs/running/[job名称]  下获取对应的内容，
     * 如果获取不到则返回一个空的RunningData对象
     * @param job_name
     * @return
     */
    public static JobRunningData getRunningData(String job_name) {
        try {
            String data = client.readData(DATAX_ADMIN_JOBS_RUNNING +"/"+ job_name, true);
            if(data != null) {
                return JSONObject.parseObject(data, JobRunningData.class);
            }
            return new JobRunningData();
        } catch(Exception e) {
            logger.error(e.getMessage(),e);
            return new JobRunningData();
        }
    }


    /**
     * 检查当前的job是否正在运行，检查方式是去
     * zookeeper的 /datax/admin/jobs/running 节点下，看是否有对应的job名称，如果有则表示当前job正在运行
     * @param job_name
     * @return
     */
    public static boolean isJobRunning(String job_name) {
        try {
            Object data = client.readData(DATAX_ADMIN_JOBS_RUNNING +"/"+ job_name, true);
            if(data != null) {
                return true;
            }
        } catch(Exception e) {
            logger.warn(e.getMessage(),e);
        }
        return false;
    }



    public static Map<String,String> replaceDyncParaTofillDate(JobConfigInfo info, Map<String,String> parameter) {
        Map<String,String> map = new HashMap<>();
        if(info == null) {
            return map;
        }
        if(StringUtils.isBlank(info.getJob_content()) ) {
            return map;
        }

        String json = info.getJob_content();
        if(json.contains(FlinkerJobConfigConstant.DATAX_CURRENT_DATE_DOLLAR_PREFIX)) {
            String fillData = parameter.get(FlinkerJobConfigConstant.DATAX_FILL_DATA);
            map.put( FlinkerJobConfigConstant.DATAX_CURRENT_DATE, fillData );
        }
        if(json.contains(FlinkerJobConfigConstant.DATAX_PRE_DATE_DOLLAR_PREFIX)) {
            String fillData = parameter.get(FlinkerJobConfigConstant.DATAX_FILL_DATA);
            map.put( FlinkerJobConfigConstant.DATAX_PRE_DATE, fillData);
        }
        return map;
    }


    /**
     * 获取job配置内容，检查job配置中是否包含
     * $DATAX_PRE_DATE
     * $$DATAX_CURRENT_DATE
     * $DATAX_LAST_EXECUTE_TIME
     * 这些参数，如果有包含，则进行过滤，将这些参数替换成指定的内容
     * @param info
     * @return
     */
    public static Map<String,String> replaceDynamicParameter(JobConfigInfo info, Map<String,String> parameter) {
        Map<String,String> map = new HashMap<>();
        if(info == null) {
            return map;
        }
        if(StringUtils.isBlank(info.getJob_content()) ) {
            return map;
        }
        String json = info.getJob_content();
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        if(json.contains(FlinkerJobConfigConstant.DATAX_CURRENT_DATE_DOLLAR_PREFIX)) {
            //查询当前日期
            logger.debug("[DataxUtil] add DATAX_CURRENT_DATE");
            String httpParameter = parameter.get( FlinkerJobConfigConstant.HTTP_PARAMETER_DATE );
            if(httpParameter != null) {
                map.put( FlinkerJobConfigConstant.DATAX_CURRENT_DATE, httpParameter );
            } else {
                Date d = new Date();
                String currentDateString = sdf.format(d);
                map.put( FlinkerJobConfigConstant.DATAX_CURRENT_DATE, currentDateString );
            }
        }

        if(json.contains(FlinkerJobConfigConstant.DATAX_PRE_DATE_DOLLAR_PREFIX)) {
            //替换前一天日期
            logger.debug("[DataxUtil] add DATAX_PRE_DATE");
            String httpParameter = parameter.get( FlinkerJobConfigConstant.HTTP_PARAMETER_DATE );
            if(httpParameter != null) {
                try {
                    Date currentDate = sdf.parse(httpParameter);
                    long preDayTime = currentDate.getTime() - ONE_DAY_TIME_BY_MILLISECOND;
                    Date preDay = new Date(preDayTime);
                    String preDayStr = sdf.format(preDay);
                    map.put( FlinkerJobConfigConstant.DATAX_PRE_DATE, preDayStr);
                } catch (ParseException e) {
                    logger.error(e.getMessage(),e);
                    throw new DynamicParamException(e.getMessage());
                }
            } else {
                Date d = new Date();
                long lastTime = d.getTime() - ONE_DAY_TIME_BY_MILLISECOND;
                Date pre = new Date( lastTime );
                String preDateString = sdf.format(pre);
                map.put( FlinkerJobConfigConstant.DATAX_PRE_DATE, preDateString);
            }

        }

        if(json.contains(FlinkerJobConfigConstant.DATAX_LAST_EXECUTE_TIME_DOLLAR_PREFIX)) {
            //替换上一次实行时间
            //根据job_id 去运行历史表里面查询 desc 创建时间
            logger.debug("[DataxUtil] add DATAX_LAST_EXECUTE_TIME");
            SimpleDateFormat lastExecuteFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
            JobExecutionInfo executionInfo = getLastSuccessExecuteJobExecutionInfo(info.getId());
            if(executionInfo == null) {
                if(info.isTiming_yn() && JobConfigInfo.TIMING_TRANSFER_TYPE_INCREMENT.equals(info.getTiming_transfer_type()) ) {
                    String httpParameterLastExecuteTime = parameter.get(FlinkerJobConfigConstant.HTTP_PARAMETER_LAST_EXECUTE_TIME);
                    if(StringUtils.isBlank(httpParameterLastExecuteTime)) {
                        //如果这个值为空，即用户没有手动传入这个值，使用一个默认值
                        httpParameterLastExecuteTime = "1970-01-01 01:01:01";
                        //throw new DynamicParamException("get last execute time is empty, must input [HTTP_PARAMETER_LAST_EXECUTE_TIME] ");
                    }
                    //将字符串解析成Date，再解析成字符串，是为了做格式校验
                    Date d = null;
                    try {
                        d = lastExecuteFormat.parse(httpParameterLastExecuteTime);
                    } catch (ParseException e) {
                        logger.error(e.getMessage(),e);
                        throw new DynamicParamException(e.getMessage());
                    }
                    String lastTime = lastExecuteFormat.format(d);
                    map.put( FlinkerJobConfigConstant.DATAX_LAST_EXECUTE_TIME, "'"+ lastTime +"'");
                }
                else {
                    //如果此任务不是定时任务，或者不是增量任务，则抛错
                    throw new DynamicParamException("current job config illegal(not timing job or not increment job) "+info.toString());
                }
            } else {
                Timestamp ts = executionInfo.getStart_time();
                String lastTime = lastExecuteFormat.format(new Date(ts.getTime()));
                map.put( FlinkerJobConfigConstant.DATAX_LAST_EXECUTE_TIME, "'"+ lastTime +"'");
            }
        }


        if(json.contains(FlinkerJobConfigConstant.DATAX_CURRENT_TIME_DOLLAR_PREFIX)) {
            logger.debug("[DataxUtil] add DATAX_CURRENT_TIME");
            String httpParameter = parameter.get( FlinkerJobConfigConstant.HTTP_PARAMETER_TIME );
            if(httpParameter != null) {
                map.put(FlinkerJobConfigConstant.DATAX_CURRENT_TIME, httpParameter);
            } else {
                Date d = new Date();
                SimpleDateFormat format = new SimpleDateFormat(TIME_FORMAT);
                String currentTimeString = format.format(d) + "00";
                map.put(FlinkerJobConfigConstant.DATAX_CURRENT_TIME, currentTimeString);
            }
        }

        if(json.contains(FlinkerJobConfigConstant.DATAX_SPECIFIED_PRE_DATE_DOLLAR_PREFIX)) {
            logger.debug("[DataxUtil] add DATAX_SPECIFIED_PRE_DATE");
            String preDateNum = JobConfigBuilder.getHDFSSpecifiedPreDate(json);
            if( StringUtils.isBlank(preDateNum) ) {
                logger.error("preDateNum is empty");
            }
            else {
                Date d = new Date();
                int day = Integer.parseInt(preDateNum);
                Calendar no = Calendar.getInstance();
                no.setTime(d);
                no.set(Calendar.DATE, no.get(Calendar.DATE) - day);
                Date d2 = no.getTime();
                String d2Str = sdf.format(d2);
                map.put(FlinkerJobConfigConstant.DATAX_SPECIFIED_PRE_DATE, d2Str);
            }
        }
        return map;

    }


    public static String preDateInitial(JobConfigInfo info) {
        String json = info.getJob_content();
        try {
            if(json.contains(FlinkerJobConfigConstant.DATAX_PRE_DATE_DOLLAR_PREFIX)) {
                //如果上一次没有执行记录，就把 pre_data 这个路径给去掉，执行一次全量操作
                JobExecutionInfo executionInfo = getLastSuccessExecuteJobExecutionInfo(info.getId());
                if(executionInfo != null) {
                    //有上一次的运行记录，继续保留pre_data，不做处理
                    return json;
                }
                else {
                    //去掉pre_data
                    json = json.replace(FlinkerJobConfigConstant.DATAX_PRE_DATA_PATH,"");
                }
            }
        }catch(Exception e) {
            logger.warn(e.getMessage(),e);
        }
        return json;
    }


    /**
     * 根据ip拼接一个获取datax机器运行状态的url
     * @param ip
     * @return
     */
    public static String getDataxMachineStateAddress(String ip) {
        return "http://" +ip+ ":"+ DATAX_DEFAULT_REST_PORT + MACHINE_STATE;
    }


    /**
     * 发送一个rest请求到一个datax admin机器，获取这台机器的系统信息
     * @param ip
     * @return
     */
    public static FlinkerMachineInfo getDataxMachineInfo(String ip) {
        try {
            String address = getDataxMachineStateAddress(ip);
            logger.debug("get datax machine url "+address);
            String result = URLConnectionUtil.retryPOST(address, "");
            logger.debug("datax machine -> "+result);
            return JSONObject.parseObject(result,FlinkerMachineInfo.class);
        } catch(Exception e) {
            logger.error(e.getMessage(),e);
            return new FlinkerMachineInfo();
        }
    }

    /**
     * 动态获取一个datax admin机器的ip，选择方式是遍历所有的datax admin机器，然后找到一台可用内存最大的机器
     * @return
     */
    public static String dynamicChoosenDataxMacheine() {
        List<String> addresses = getDataxMachineAddress();
        Map<String,FlinkerMachineInfo> map = new HashedMap();
        String iteratorAddress = "";
        long lastIteratorMaxMemory = Long.MAX_VALUE;
        for(int i=0;i<addresses.size();i++) {
            FlinkerMachineInfo info = getDataxMachineInfo(addresses.get(i));
            long currentFreeMemory = info.getFreeMemory();
            if(i==0) {
                iteratorAddress = addresses.get(i);
                lastIteratorMaxMemory = currentFreeMemory;
            } else {
                if(lastIteratorMaxMemory < currentFreeMemory) {
                    iteratorAddress = addresses.get(i);
                    lastIteratorMaxMemory = currentFreeMemory;
                }
            }
        }

        if(StringUtils.isBlank(iteratorAddress)) {
            logger.error("can not get dynamic datax machine info ");
        }
        return iteratorAddress;
    }


    public static String assembleMailInfo(MediaSourceInfo srcInfo, MediaSourceInfo destInfo, String names, List<String> paths) throws Exception {
        StringBuffer buf = new StringBuffer();
        buf.append("hi:").append("<br>").append("<br>");
        String env = ConfigReadUtil.getString("datax.env");
        buf.append("&nbsp &nbsp &nbsp").append("当前环境 : ").append(env).append("<br/>");
        buf.append("&nbsp &nbsp &nbsp").append("您有个数据同步任务").append("从").append(srcInfo.getName()).append("(").append(srcInfo.getParameterObj().getMediaSourceType().name()).append(")  ")
                .append("同步到").append(destInfo.getName()).append("(").append(destInfo.getParameterObj().getMediaSourceType().name()).append(")  ").append(",请查看").append("<br>");

        //增加hdfs写入路径信息
        String hdfsInfo = assembleHDFSPathInfo(srcInfo,destInfo,names,paths);
        if(StringUtils.isNotBlank(hdfsInfo)) {
            buf.append(hdfsInfo);
        }
        if (StringUtils.isNotBlank(names)) {
            String[] tables = names.split(",");
            for (String t : tables) {
                buf.append("").append("<br>");
                buf.append("表名称:").append(t).append("<br>");
                List<ColumnMeta> columns = MetaManager.getColumns(srcInfo, t);
                buf.append(assembleTableInfo(columns, srcInfo, destInfo));
                buf.append("</table>");
                buf.append("<br/><br/>");
                buf.append("<hr/>");
            }//end for
        }
        return buf.toString();
    }

    private static String assembleHDFSPathInfo(MediaSourceInfo srcInfo, MediaSourceInfo destInfo, String names, List<String> paths) {
        if(destInfo.getType() != MediaSourceType.HDFS) {
            return "<br/>";
        }
        if (StringUtils.isNotBlank(names)) {
            StringBuilder sb = new StringBuilder();
            String[] tables = names.split(",");
            if(tables.length>0) {
                sb.append("HDFS写入的路径如下：<br/>");
            }
            for(String p : paths) {
                if(StringUtils.isNotBlank(p)) {
                    sb.append(p).append("<br/>");
                }
            }
            return sb.toString();
        }
        return null;
    }


    private static String assembleTableInfo(List<ColumnMeta> columns, MediaSourceInfo info, MediaSourceInfo destInfo) {
        boolean needTransform = false;
        StringBuilder buf = new StringBuilder();
        if (MediaSourceType.HBASE == info.getType()) {
            if (MediaSourceType.ELASTICSEARCH == destInfo.getType() || MediaSourceType.HDFS == destInfo.getType() || MediaSourceType.POSTGRESQL == destInfo.getType()) {
                buf.append("<table border='1'>").append("<tr><td>列族名称</td><td>字段名称</td><td>转换后的类型</td></tr>");
                needTransform = true;
            } else {
                buf.append("<table border='1'>").append("<tr><td>列族名称</td><td>字段名称</td></tr>");
            }

        } else if (MediaSourceType.MYSQL == info.getType() || MediaSourceType.SQLSERVER == info.getType() || MediaSourceType.POSTGRESQL == info.getType()) {
            if (MediaSourceType.ELASTICSEARCH == destInfo.getType() || MediaSourceType.HDFS == destInfo.getType()) {
                buf.append("<table border='1'>").append("<tr><td>字段名称</td><td>字段类型</td><td>字段长度</td><td>字段精度</td><td>字段描述</td><td>转换后的类型</td></tr>");
                needTransform = true;
            } else {
                buf.append("<table border='1'>").append("<tr><td>字段名称</td><td>字段类型</td><td>字段长度</td><td>字段精度</td><td>字段描述</td></tr>");
            }
        } else {
            if (info.getType() != destInfo.getType() && (MediaSourceType.ELASTICSEARCH == info.getType() || MediaSourceType.HDFS == info.getType())) {
                buf.append("<table border='1'>").append("<tr><td>字段名称</td><td>字段类型</td><td>字段描述</td><td>转换后的类型</td></tr>");
                needTransform = true;
            } else {
                buf.append("<table border='1'>").append("<tr><td>字段名称</td><td>字段类型</td><td>字段描述</td></tr>");
            }
        }

        MediaMeta target = new MediaMeta();
        if (needTransform) {
            MediaMeta mm = new MediaMeta();
            mm.setColumn(columns);
            mm.setName(info.getName());
            mm.setNameSpace(info.getParameterObj().getNamespace());
            mm.setDbType(info.getType());
            if (destInfo.getType() == MediaSourceType.ELASTICSEARCH) {
                target = MetaMapping.transformToES(mm);
            } else if (destInfo.getType() == MediaSourceType.HBASE) {
                target = MetaMapping.transformToHBase(mm);
            } else if (destInfo.getType() == MediaSourceType.HDFS) {
                target = MetaMapping.transformToHDFS(mm);
            } else if (destInfo.getType() == MediaSourceType.MYSQL || destInfo.getType() == MediaSourceType.SQLSERVER || destInfo.getType() == MediaSourceType.POSTGRESQL) {
                target = MetaMapping.transformToRDBMS(mm);
            }
        }

        Map<String, ColumnMeta> map = new HashMap<>();
        if (target.getColumn() != null) {
            List<ColumnMeta> targetColumns = target.getColumn();
            for (ColumnMeta cm : targetColumns) {
                map.put(cm.getName(), cm);
            }
        }

        if (columns != null && columns.size() > 0) {
            for (ColumnMeta cm : columns) {
                ColumnMeta targetColumn = map.get(cm.getName());
                if (targetColumn == null) {
                    targetColumn = new ColumnMeta();
                    targetColumn.setType("");
                }
                if (MediaSourceType.HBASE == info.getType()) {
                    if (MediaSourceType.ELASTICSEARCH == destInfo.getType() || MediaSourceType.HDFS == destInfo.getType() || MediaSourceType.POSTGRESQL == destInfo.getType()) {
                        buf.append("<tr><td>").append(cm.getColumnFamily()).append("</td>").append("<td>").append(cm.getName()).append("</td>").append("<td>").append(targetColumn.getType()).append("</td></tr>");
                    } else {
                        buf.append("<tr><td>").append(cm.getColumnFamily()).append("</td>").append("<td>").append(cm.getName()).append("</td></tr>");
                    }
                } else if (MediaSourceType.MYSQL == info.getType() || MediaSourceType.SQLSERVER == info.getType() || MediaSourceType.POSTGRESQL == info.getType()) {
                    //如果目标端是HDFS或者ES，则把转换后的类型也显示出来
                    if (MediaSourceType.ELASTICSEARCH == destInfo.getType() || MediaSourceType.HDFS == destInfo.getType()) {
                        String columnDesc = "";
                        if (StringUtils.isNotBlank(cm.getColumnDesc())) {
                            columnDesc = cm.getColumnDesc();
                        }
                        buf.append("<tr><td>").append(cm.getName()).append("</td>").append("<td>").append(cm.getType()).append("</td>");
                        buf.append("<td>").append(cm.getLength()).append("</td>").append("<td>").append(cm.getDecimalDigits()).append("</td>").append("<td>").append(columnDesc).append("</td>").
                                append("<td>").append(targetColumn.getType()).append("</td></tr>");
                    } else {
                        String columnDesc = "";
                        if (StringUtils.isNotBlank(cm.getColumnDesc())) {
                            columnDesc = cm.getColumnDesc();
                        }
                        buf.append("<tr><td>").append(cm.getName()).append("</td>").append("<td>").append(cm.getType()).append("</td>");
                        buf.append("<td>").append(cm.getLength()).append("</td>").append("<td>").append(cm.getDecimalDigits()).append("</td>").append("<td>").append(columnDesc).append("</td></tr>");
                    }
                } else {
                    String columnDesc = "";
                    if (StringUtils.isNotBlank(cm.getColumnDesc())) {
                        columnDesc = cm.getColumnDesc();
                    }
                    if (info.getType() != destInfo.getType() && (MediaSourceType.ELASTICSEARCH == info.getType() || MediaSourceType.HDFS == info.getType())) {
                        buf.append("<tr><td>").append(cm.getName()).append("</td>").append("<td>").append(cm.getType()).append("</td>").append("<td>").append(columnDesc).append("</td>").
                                append("<td>").append(targetColumn.getType()).append("</td></tr>");
                    } else {
                        buf.append("<tr><td>").append(cm.getName()).append("</td>").append("<td>").append(cm.getType()).append("</td>").append("<td>").append(columnDesc).append("</td></tr>");
                    }
                }
            }
        }
        return buf.toString();
    }

    public static String parseHDFSWritePath(JobConfigInfo info) {
        MediaSourceInfo targetMediaSourceInfo = getMediaSourceInfo( info.getJob_target_media_source_id() );
        if(targetMediaSourceInfo.getType() != MediaSourceType.HDFS) {
            return "";
        }
        DLConfig connConf = DLConfig.parseFrom(info.getJob_content());
        String jsonPath = "job.content[0].writer.parameter.path";
        String path = getHdfsPath(connConf,jsonPath);
        return path;
    }


    private static String getHdfsPath(DLConfig config,String path) {
        try {
            return (String)config.get(path);
        } catch(Exception e) {
            return "";
        }
    }

    public static String randomString(int len) {
        len = 10;
        String chars = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";    /****默认去掉了容易混淆的字符oOLl,9gq,Vv,Uu,I1****/
        int maxPos = chars.length();
        String pwd = "";
        for (int i = 0; i < len; i++) {
            pwd += chars.charAt((int) Math.floor(Math.random() * maxPos));
        }
        return pwd;
    }



    public static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    public static String formatJson(String json) {
        int i = 0;
        int len = 0;
        String tab = "    ";
        String targetJson = "";
        int indentLevel = 0;
        boolean inString = false;
        char currentChar = ' ';
        for (i = 0, len = json.length(); i < len; i += 1) {
            currentChar = json.charAt(i);
            switch (currentChar) {
                case '{':
                case '[':
                    if (!inString) {
                        targetJson += currentChar + "\n" + repeat(tab, indentLevel + 1);
                        indentLevel += 1;
                    } else {
                        targetJson += currentChar;
                    }
                    break;
                case '}':
                case ']':
                    if (!inString) {
                        indentLevel -= 1;
                        targetJson += "\n" + repeat(tab, indentLevel) + currentChar;
                    } else {
                        targetJson += currentChar;
                    }
                    break;
                case ',':
                    if (!inString) {
                        targetJson += ",\n" + repeat(tab, indentLevel);
                    } else {
                        targetJson += currentChar;
                    }
                    break;
                case ':':
                    if (!inString) {
                        targetJson += ": ";
                    } else {
                        targetJson += currentChar;
                    }
                    break;
                case ' ':
                case '\n':
                case '\t':
                    if (inString) {
                        targetJson += currentChar;
                    }
                    break;
                case '"':
                    if (i > 0 && json.charAt(i - 1) != '\\') {
                        inString = !inString;
                    }
                    targetJson += currentChar;
                    break;
                default:
                    targetJson += currentChar;
                    break;
            }
        }
        return targetJson;
    }



    public static void main(String[] args) {
        Date d = new Date();
        SimpleDateFormat format = new SimpleDateFormat(TIME_FORMAT);
        String currentTimeString = format.format(d) +"-00";
        System.out.println(currentTimeString);
    }


}
