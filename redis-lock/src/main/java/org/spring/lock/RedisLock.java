package org.spring.lock;

import org.spring.utils.FileUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

@Service
public class RedisLock implements Lock {

	private static final String  KEY = "LOCK_KEY";


//	@Resource
//	private JedisConnectionFactory factory;

	private ThreadLocal<String> local = new ThreadLocal<>();
    @Resource
    private RedisTemplate<String,String> redisTemplate;

	@Override
	//阻塞式的加锁
	public void lock() {
		//1.尝试加锁
		if(tryLock()){
			return;
		}
		//2.加锁失败，当前任务休眠一段时间
		try {
			Thread.sleep(10);//性能浪费
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//3.递归调用，再次去抢锁
		lock();
	}



	@Override
	//阻塞式加锁,使用setNx命令返回OK的加锁成功，并生产随机值
	public boolean tryLock() {
		//产生随机值，标识本次锁编号
		String uuid = UUID.randomUUID().toString();

		/**
		 * key:我们使用key来当锁
		 * uuid:唯一标识，这个锁是我加的，属于我
		 * NX：设入模式【SET_IF_NOT_EXIST】--仅当key不存在时，本语句的值才设入
		 * PX：给key加有效期
		 * 1000：有效时间为 1 秒
		 */
        boolean b = setIfAbsent(KEY, uuid, 60);
        //设值成功--抢到了锁
        if(b){
            local.set(uuid);//抢锁成功，把锁标识号记录入本线程--- Threadlocal
            return b;
        }

        //key值里面有了，我的uuid未能设入进去，抢锁失败
        return b;
	}

	//错误解锁方式
	public void unlockWrong() {
		//获取redis的原始连接
		String uuid = redisTemplate.opsForValue().get(KEY);
		//uuid与我的相等，证明这是我当初加上的锁
		if (null != uuid && uuid.equals(local.get())){//现在锁还是自己的
			//锁失效了

			//删锁
            redisTemplate.delete(KEY);
		}
	}

	//正确解锁方式
	public void unlock() {
		//获取redis的原始连接
        List<String> keys = Arrays.asList(KEY, local.get());

        //lua方式一
		//定义释放锁的lua脚本
		DefaultRedisScript<Long> UNLOCK_LUA_SCRIPT = new DefaultRedisScript<>(
				"if redis.call(\"get\",KEYS[1]) == KEYS[2] then return redis.call(\"del\",KEYS[1]) else return -1 end"
				, Long.class
		);
        Long result = redisTemplate.execute(UNLOCK_LUA_SCRIPT, keys);

		//lua方式二 ，读取文件的方式要注意空格是否被idea截取了
//		String script = FileUtils.getScript("unlock.lua");
        //通过原始连接连接redis执行lua脚本
//		Long result=redisTemplate.execute(new DefaultRedisScript<>(script,Long.class), keys);
	}

	//-----------------------------------------------

	@Override
	public Condition newCondition() {
		return null;
	}

	@Override
	public boolean tryLock(long time, TimeUnit unit)
			throws InterruptedException {
		return false;
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
	}
    /**
     *
     * @param key
     * @param value
     * @param exptime 单位s
     * @return 设置成功 返回 true，否则 false
     */
    public  boolean setIfAbsent(final String key, final String value, final long exptime) {
        Boolean b = redisTemplate.execute(new RedisCallback<Boolean>() {
            @Override
            public Boolean doInRedis(RedisConnection connection) throws DataAccessException {
                Object obj = connection.execute("set", key.getBytes(),
                        value.getBytes(),
                        "NX".getBytes(),
                        "EX".getBytes(),
                        String.valueOf(exptime).getBytes());
                //要么返回OK，要么返回null
                return obj!=null;
            }
        });
        return b;
    }

}
