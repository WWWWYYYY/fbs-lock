package org.spring.lock;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service("zkLock")
public class ZkLock implements Lock {
    private CountDownLatch countDownLatch= null;

    private String beforePath;//当前请求的节点前一个节点
    private String currentPath;//当前请求的节点

    // zk连接地址
    private static final String CONNECTSTRING = "127.0.0.1:2181";
    // 创建zk连接
    protected ZkClient zkClient = new ZkClient(CONNECTSTRING);
    protected static final String PATH = "/fbslock";

    //reentrantLock的作用就是让一个服务上的所有应用只有一个线程可以使用zk客户端。ZkLock的作用是让分布式环境下只有一个节点的得到操作的权利
    private ReentrantLock reentrantLock =new ReentrantLock();
    @Override
    public void lock() {
        //尝试获得锁资源
        if (tryLock()) {
            System.out.println("##获取lock锁的资源####");
        } else {
            // 等待
            waitLock();
            // 重新获取锁资源
            lock();
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean  tryLock() {
        reentrantLock.lock();
        //如果currentPath为空则为第一次尝试加锁，第一次加锁赋值currentPath
        if(currentPath == null || currentPath.length()<= 0){
            //创建一个临时顺序节点
            currentPath = this.zkClient.createEphemeralSequential(PATH + '/',"lock");
        }
        //获取所有临时节点并排序，临时节点名称为自增长的字符串如：0000000400
        List<String> childrens = this.zkClient.getChildren(PATH);
        Collections.sort(childrens);

        if (currentPath.equals(PATH + '/'+childrens.get(0))) {//如果当前节点在所有节点中排名第一则获取锁成功
            return true;
        } else {//如果当前节点在所有节点中排名中不是排名第一，则获取前面的节点名称，并赋值给beforePath
            int wz = Collections.binarySearch(childrens, currentPath.substring(7));
            beforePath = PATH + '/'+childrens.get(wz-1);
        }
        return false;

    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        //删除当前临时节点

        zkClient.delete(currentPath);
        currentPath=null;
        reentrantLock.unlock();
//        zkClient.close();
    }

    @Override
    public Condition newCondition() {
        return null;
    }


    public void waitLock() {
        IZkDataListener listener = new IZkDataListener() {

            public void handleDataDeleted(String dataPath) throws Exception {

                if(countDownLatch!=null){
                    countDownLatch.countDown();
                }
            }

            public void handleDataChange(String dataPath, Object data) throws Exception {

            }
        };
        //给排在前面的的节点增加数据删除的watcher,本质是启动另外一个线程去监听前置节点
        this.zkClient.subscribeDataChanges(beforePath, listener);

        if(this.zkClient.exists(beforePath)){
            countDownLatch=new CountDownLatch(1);
            try {
                countDownLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.zkClient.unsubscribeDataChanges(beforePath, listener);
    }

}
