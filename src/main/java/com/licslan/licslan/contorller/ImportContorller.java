package com.licslan.licslan.contorller;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.licslan.licslan.dao.ImportDao;
import com.licslan.licslan.po.User;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.jeecgframework.poi.excel.ExcelImportUtil;
import org.jeecgframework.poi.excel.entity.ImportParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
/**
 * ====================================================================================================
 * @author LICSLAN
 * 本次实验的目的是为了导入大量数据进行测试 2000万单表数据
 * 环境要求：一台基础的阿里云ECS  非必需  本地环境centos环境也可以替代
 * PC xiaomi 8G i7 256g SSD  非必需  window/Linux/mac都可以
 * docker 环境  docker 运行MySQL环境一套  必需项
 * springboot相关依赖 mybatis easypoi maven jdk8  必需项
 * 需要对多线程基础知识的了解  必需项
 * docker 常用命令使用  必需项
 * 常用的Linux操作命令使用  必需项  比如防火墙设置  上传下载
 * git命令的熟悉  非必需
 * 数据表设计 SQL 基本使用  必需项
 * shell 脚本使用  非必需
 * =====================================================================================================
 * 初级阶段我们创建线程主要有两种方法：一种是直接继承Thread类，一种是实现Runnable接口，
 * 但是这两种方法都无法返回执行结果；如果需要获取执行结果，就必须通过共享变量或者使用线程通信的方式来达到效果，实现起来比较麻烦。
 * java1.5之后，就提供了Callable和Future，通过这两种方法可以在执行结束后返回执行结果。
 *
 *  如果在并发执行的任务中，并且每个任务之后都需要获取结果，有两种方式可以实现：
 *  第一种：通过一个list保存一组future，循环查看结果，future不一定完成，如果没有完成，
 *  则调用get会发生阻塞；这样如果排在前面的任务没有完成，就会发生阻塞，后面已经完成的任务就无法获取结果
 *  第二种：
 *  改进：future获取结果之前先判断future是否执行完毕（f.isDone()），如果执行完成，获取结果之后，则从执行列表中删除任务。
 *  使用ExecutorCompletionService来管理线程池执行任务的执行结果
 *  =====================================================================================================
 * */
@RestController
@Slf4j
public class ImportContorller {
    private final ImportDao importDao;
    private final static Integer BATCH_PER = 10000;

    @Autowired
    public ImportContorller(ImportDao importDao) {
        this.importDao = importDao;
    }

    @RequestMapping("/")
    public String test(){
        return "hello test";
    }

    @RequestMapping("/save")
    public int save(@RequestBody List<User> user){
        return importDao.save(user);
    }


    @RequestMapping("/importExcel")
    public synchronized String importEx(@Param("file")MultipartFile file){
        Integer result=0;
        ImportParams params = new ImportParams();
        params.setTitleRows(1);
        params.setHeadRows(1);
        params.setNeedVerfiy(false);
        long start = System.currentTimeMillis();
        try {
            List<User> userList = ExcelImportUtil.importExcel(file.getInputStream(),
                    User.class, params);
            int size = userList.size();
            ExecutorCompletionService<Integer> pool = getExecutorCompletionService();
            if (size > BATCH_PER) {
                int batch = size % BATCH_PER == 0 ? size / BATCH_PER : size / BATCH_PER + 1;
                //支持多线程版本
                for (int j = 0; j < batch; j++) {
                    int end = (j + 1) * BATCH_PER;
                    if (end > size) {
                        end = size;
                    }
                    List<User> subList = userList.subList(j * BATCH_PER, end);
                    //提交任务
                    pool.submit(new MyThread(subList));
                    //获取任务结果
                    int perResult = pool.take().get();
                    log.info("每次执行的结果"+perResult);
                    result += perResult;
                    log.info("每次计算后的执行结果"+result);
                }
                //小于10000条
            }else {
                result  = getPool().submit(new MyThread(userList)).get();
                log.info("  TODO  "+result);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        long end = System.currentTimeMillis();
        log.info("插入[ "+result+" ]数据总耗时[ "+(end-start)/1000+" ]秒"+"最后统计结果是 "+result);
        return result.toString();
    }
    private static ThreadPoolExecutor getPool() {
        return (new ThreadPoolExecutor(
                16,
                32,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                new ThreadFactoryBuilder().setNameFormat("Import-task-%d").build()));
    }
    private static ExecutorCompletionService<Integer> getExecutorCompletionService() {
        return new ExecutorCompletionService<>(new ThreadPoolExecutor(
                16,
                32,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(200),
                new ThreadFactoryBuilder().setNameFormat("Import-task-%d").build()));
    }
    public class MyThread implements Callable<Integer> {
        private List<User> subList;
        MyThread(List<User> subList){
            this.subList=subList;
        }

        @Override
        public Integer call() {
            log.info("=========当前正在执行的线程名称是========"+Thread.currentThread().getName());
            return importDao.save(subList);
        }
    }


    public static void main(String[] args) throws ExecutionException,InterruptedException{
        long start = System.currentTimeMillis();
        ExecutorService exe = Executors.newFixedThreadPool(5);
        ExecutorCompletionService<Integer> ecs = new ExecutorCompletionService<>(exe);
        for(int i = 0 ; i <= 10000 ; i++){
            int x = i;
            ecs.submit(
                    () -> x
            );
        }
        System. out.println("begin to get result" );
        Integer result=0;
        int count = 0;
        for(int i = 0 ; i <= 10000 ; i++){
            Future<Integer> f = ecs.take();
            System. out.println(f.get());
            log.info("每次执行结果是~~~~~"+f.get());
            result += f.get();
            log.info("每次循环计算一次的结果是~~~~~"+result);
            count++;
        }
        System. out.println("task has done:" +count );
        log.info("总的统计结果是"+result);
        long end = System.currentTimeMillis();
        log.info("耗时结果是"+(end-start)+"毫秒");
        exe.shutdown();
    }

    private  void  methodFuture() throws ExecutionException, InterruptedException {
        ExecutorService exe = Executors.newFixedThreadPool(5);
        List<Future<String>> result = new ArrayList<>();
        Random random = new Random();
        for(int i = 0 ; i < 10 ; i++){
            result.add( exe.submit(() -> {
                Thread. sleep(random.nextInt(10000));
                return Thread.currentThread().getName();
            }));
        }
        log.info("start to get result:" );
        int count = 0;
        for(Future<String> f : result ){
            log.info(f .get());
            count++;
        }
        log.info("task has done:" +count );
        exe .shutdown();
    }

}
