package org.mysql.lock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

@Service
public class MysqlLock implements Lock {
	
	private static final int ID_NUM = 1;

	@Resource
	private JdbcTemplate jdbcTemplate;
	
	@Override
	//阻塞式的加锁
	public void lock() {//锁 --- 为了排队
		//1.尝试加锁
		if(tryLock()){
			return;
		}
		//2.加锁失败，当前任务休眠一段时间
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		//3.递归调用，再次重新加锁
		lock();
	}



	@Override
	//非阻塞式加锁,往数据库写入id为1的数据，能写成功的即加锁成功
	public boolean tryLock() {
		try {
			String sql = "insert into db_lock (id) values (?)";
			jdbcTemplate.update(sql,ID_NUM); //一条sql语句，是个原子性操作
//			mapper.insert(ID_NUM);
		} catch (Exception e) {
			return false;
		}
		return true;
	}


	
	@Override
	//解锁
	public void unlock() {
		String sql = "delete from db_lock where id = ?";
		jdbcTemplate.update(sql,ID_NUM);
//		mapper.deleteByPrimaryKey(ID_NUM);
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

}
