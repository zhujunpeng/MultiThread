package com.example.mutithreaddemo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.R.integer;
import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener{

    protected static final int DOWNLOAD_ERROR = 0;
	public static final int THREAD_ERROR = 1;
	public static final int DOWNLOAD_FINISH = 2;
	private static final String TAG = "MainActivity";
	private EditText etHttp,etThreadCount;
	private Button btnDowload;
	private LinearLayout ll_container;
	// 进度条集合
	private List<ProgressBar> pbs;
	
	// 线程的数量
	private static int threadCount = 3;
	// 每个下载区块的大小
	private static long blocksize;
	// 正在运行的线程数量
	private static int runningThreadCount;
	
	private Handler handler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case DOWNLOAD_ERROR:
				Toast.makeText(getApplicationContext(), "下载失败！", Toast.LENGTH_SHORT).show();
				break;
			case THREAD_ERROR:
				Toast.makeText(getApplicationContext(), "线程下载失败！", Toast.LENGTH_SHORT).show();
				break;
			case DOWNLOAD_FINISH:
				Toast.makeText(getApplicationContext(), "下载成功！", Toast.LENGTH_SHORT).show();
				break;
			default:
				break;
			}
		}
		
	};

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        etHttp = (EditText) findViewById(R.id.et_http);
        etThreadCount = (EditText) findViewById(R.id.et_threadcount);
        ll_container = (LinearLayout) findViewById(R.id.ll_container);
        btnDowload = (Button) findViewById(R.id.btn_download);
        btnDowload.setOnClickListener(this);
       
    }

	@Override
	public void onClick(View v) {
		final String path = etHttp.getText().toString();
		String threadCountstr = etThreadCount.getText().toString();
		Log.i(TAG, "开启线程数量：" + threadCountstr);
		threadCount = Integer.parseInt(threadCountstr);
		
		if (TextUtils.isEmpty(path)) {
			Toast.makeText(this, "网址不能为空！", Toast.LENGTH_SHORT).show();
			return;
		}
		if (TextUtils.isEmpty(threadCountstr)) {
			Toast.makeText(this, "开启的线程数量不能为空", Toast.LENGTH_SHORT).show();
			return;
		}
		
		// 清空一起拿残留的progressbar
		ll_container.removeAllViews();
		// 在界面上添加threadCount个进度条
		pbs = new ArrayList<ProgressBar>();
		for (int i = 0; i < threadCount; i++) {
			ProgressBar pb = (ProgressBar) View.inflate(getApplicationContext(), R.layout.pb, null);
			ll_container.addView(pb);
			pbs.add(pb);
		}
		
		Toast.makeText(this, "开始下载", 0).show();
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					// 服务器文件的路径
//					String path = "http://192.168.1.3:8080/gg.exe";
					URL url = new URL(path);
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(5000);
					int responseCode = conn.getResponseCode();
					if (responseCode == 200) {
						// 获取服务器端返回的数据大小
						long size = conn.getContentLength();
						System.out.println("服务器文件大小： " + size);
						
						blocksize = size / threadCount;
						// 1. 首先在本地创建一个大小跟服务器大小一样的空白文件
						File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
						// 可读可写
						RandomAccessFile raf = new RandomAccessFile(file, "rw");
						// 设置文件大小
						raf.setLength(size);
						
						// 2 开启若干个子线程分别去下载相应的资源
						runningThreadCount = threadCount;
						for (int i = 1; i < threadCount + 1; i++) {
							long startIndex = (i - 1) * blocksize;
							long endIndex = i * blocksize - 1;
							if (i == threadCount) {
								// 最后一个线程
								endIndex = size - 1;
							}
							System.out.println("开启线程：" + i + "下载的位置：" + startIndex + "~"
									+ endIndex);
							
							int threadSize = (int) (endIndex - startIndex);
							pbs.get(i).setMax(threadSize);
							new DownloadThread(i, startIndex, endIndex, path).start();
						}
					}
					conn.disconnect();
				} catch (Exception e) {
					e.printStackTrace();
					
					Message msg = Message.obtain();
					msg.what = DOWNLOAD_ERROR;
					handler.sendMessage(msg);
				}
			}
		}).start();
	
	}
	
private class DownloadThread extends Thread{
		
		private int id;
		private long startIndex;
		private long endIndex;
		private String path;
		public DownloadThread(int id, long startIndex, long endIndex,
				String path) {
			super();
			this.id = id;
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.path = path;
		}
		public void run() {
			super.run();
			
			try {
				int total = 0;
				File positionFile = new File(Environment.getExternalStorageDirectory(),getFileName(path) + id + ".txt");
				URL url = new URL(path);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				// 设置为get的方式
				conn.setRequestMethod("GET");
				
				// 接着从上一次的位置继续下载数据
				if (positionFile.exists() && positionFile.length() > 0) {// 判断是否有记录
					FileInputStream fis = new FileInputStream(positionFile);
					BufferedReader br = new BufferedReader(
							new InputStreamReader(fis));
					// 获取当前线程上次下载的总大小是多少
					String lasttotalstr = br.readLine();
//					int lastTotal = Integer.parseInt(lasttotalstr);
					int lastTotal = Integer.valueOf(lasttotalstr); 
					System.out.println("上次线程" + id + "下载的总大小："
							+ Integer.valueOf(lasttotalstr));
					startIndex += lastTotal;
					total += lastTotal;// 加上上次下载的总大小。
					fis.close();
				}
				
//				System.out.println("上次下载的进程 :" + lasttotal);
				// 请求头，必须要设置
				conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);
				conn.setConnectTimeout(5000);
				int responseCode = conn.getResponseCode();
				if (responseCode == 206) {
					InputStream is = conn.getInputStream();
					// 文件存储位置
					File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
					// 可读可写
					RandomAccessFile raf = new RandomAccessFile(file, "rw");
					raf.seek(startIndex);
					System.out.println("第" + id + "个线程：写文件的开始位置："
							+ String.valueOf(startIndex));
					int len = 0;
					byte[] buffer = new byte[1024];
					
					
					
					// 文件没有到末尾
					while ((len = is.read(buffer)) != -1) {
						// 这个进度文件写入不完整,因为存在硬盘缓存区，没有来的及写到硬盘中
//						FileOutputStream fos = new FileOutputStream(positionFile);
						// 
						RandomAccessFile rf = new RandomAccessFile(positionFile, "rwd");
						raf.write(buffer, 0, len);
						
						total += len;
						rf.write(String.valueOf(total).getBytes());
						rf.close();
						pbs.get(id).setProgress(total);
					}
					is.close();
					raf.close();
					System.out.println("线程" + id + "下载完毕");
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				
				Message msg = Message.obtain();
				msg.what = THREAD_ERROR;
				handler.sendMessage(msg);
			} finally{
				
				// 只有所有的线程都下载完毕后 才可以删除记录文件
				synchronized (MainActivity.class) {
					System.out.println("线程"+ id +"下载完毕了");
					runningThreadCount --;
					if (runningThreadCount < 1) {
						System.out.println("所有的线程都工作完毕了。删除临时记录的文件");
						for (int i = 0; i < threadCount; i++) {
							File f = new File(Environment.getExternalStorageDirectory(),getFileName(path) + i + ".txt");
							System.out.println(f.delete());
						}
				}
				Message msg = Message.obtain();
				msg.what = DOWNLOAD_FINISH;
				handler.sendMessage(msg);
				}
			}
		
		}
	}
	// http://192.168.1.3:8080/ff.exe
	private String getFileName(String path){
		int start = path.lastIndexOf("/")+1;
		return path.substring(start);
	}
    
}
