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
	// ����������
	private List<ProgressBar> pbs;
	
	// �̵߳�����
	private static int threadCount = 3;
	// ÿ����������Ĵ�С
	private static long blocksize;
	// �������е��߳�����
	private static int runningThreadCount;
	
	private Handler handler = new Handler(){

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case DOWNLOAD_ERROR:
				Toast.makeText(getApplicationContext(), "����ʧ�ܣ�", Toast.LENGTH_SHORT).show();
				break;
			case THREAD_ERROR:
				Toast.makeText(getApplicationContext(), "�߳�����ʧ�ܣ�", Toast.LENGTH_SHORT).show();
				break;
			case DOWNLOAD_FINISH:
				Toast.makeText(getApplicationContext(), "���سɹ���", Toast.LENGTH_SHORT).show();
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
		Log.i(TAG, "�����߳�������" + threadCountstr);
		threadCount = Integer.parseInt(threadCountstr);
		
		if (TextUtils.isEmpty(path)) {
			Toast.makeText(this, "��ַ����Ϊ�գ�", Toast.LENGTH_SHORT).show();
			return;
		}
		if (TextUtils.isEmpty(threadCountstr)) {
			Toast.makeText(this, "�������߳���������Ϊ��", Toast.LENGTH_SHORT).show();
			return;
		}
		
		// ���һ���ò�����progressbar
		ll_container.removeAllViews();
		// �ڽ��������threadCount��������
		pbs = new ArrayList<ProgressBar>();
		for (int i = 0; i < threadCount; i++) {
			ProgressBar pb = (ProgressBar) View.inflate(getApplicationContext(), R.layout.pb, null);
			ll_container.addView(pb);
			pbs.add(pb);
		}
		
		Toast.makeText(this, "��ʼ����", 0).show();
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					// �������ļ���·��
//					String path = "http://192.168.1.3:8080/gg.exe";
					URL url = new URL(path);
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");
					conn.setConnectTimeout(5000);
					int responseCode = conn.getResponseCode();
					if (responseCode == 200) {
						// ��ȡ�������˷��ص����ݴ�С
						long size = conn.getContentLength();
						System.out.println("�������ļ���С�� " + size);
						
						blocksize = size / threadCount;
						// 1. �����ڱ��ش���һ����С����������Сһ���Ŀհ��ļ�
						File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
						// �ɶ���д
						RandomAccessFile raf = new RandomAccessFile(file, "rw");
						// �����ļ���С
						raf.setLength(size);
						
						// 2 �������ɸ����̷ֱ߳�ȥ������Ӧ����Դ
						runningThreadCount = threadCount;
						for (int i = 1; i < threadCount + 1; i++) {
							long startIndex = (i - 1) * blocksize;
							long endIndex = i * blocksize - 1;
							if (i == threadCount) {
								// ���һ���߳�
								endIndex = size - 1;
							}
							System.out.println("�����̣߳�" + i + "���ص�λ�ã�" + startIndex + "~"
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
				// ����Ϊget�ķ�ʽ
				conn.setRequestMethod("GET");
				
				// ���Ŵ���һ�ε�λ�ü�����������
				if (positionFile.exists() && positionFile.length() > 0) {// �ж��Ƿ��м�¼
					FileInputStream fis = new FileInputStream(positionFile);
					BufferedReader br = new BufferedReader(
							new InputStreamReader(fis));
					// ��ȡ��ǰ�߳��ϴ����ص��ܴ�С�Ƕ���
					String lasttotalstr = br.readLine();
//					int lastTotal = Integer.parseInt(lasttotalstr);
					int lastTotal = Integer.valueOf(lasttotalstr); 
					System.out.println("�ϴ��߳�" + id + "���ص��ܴ�С��"
							+ Integer.valueOf(lasttotalstr));
					startIndex += lastTotal;
					total += lastTotal;// �����ϴ����ص��ܴ�С��
					fis.close();
				}
				
//				System.out.println("�ϴ����صĽ��� :" + lasttotal);
				// ����ͷ������Ҫ����
				conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);
				conn.setConnectTimeout(5000);
				int responseCode = conn.getResponseCode();
				if (responseCode == 206) {
					InputStream is = conn.getInputStream();
					// �ļ��洢λ��
					File file = new File(Environment.getExternalStorageDirectory(),getFileName(path));
					// �ɶ���д
					RandomAccessFile raf = new RandomAccessFile(file, "rw");
					raf.seek(startIndex);
					System.out.println("��" + id + "���̣߳�д�ļ��Ŀ�ʼλ�ã�"
							+ String.valueOf(startIndex));
					int len = 0;
					byte[] buffer = new byte[1024];
					
					
					
					// �ļ�û�е�ĩβ
					while ((len = is.read(buffer)) != -1) {
						// ��������ļ�д�벻����,��Ϊ����Ӳ�̻�������û�����ļ�д��Ӳ����
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
					System.out.println("�߳�" + id + "�������");
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				
				Message msg = Message.obtain();
				msg.what = THREAD_ERROR;
				handler.sendMessage(msg);
			} finally{
				
				// ֻ�����е��̶߳�������Ϻ� �ſ���ɾ����¼�ļ�
				synchronized (MainActivity.class) {
					System.out.println("�߳�"+ id +"���������");
					runningThreadCount --;
					if (runningThreadCount < 1) {
						System.out.println("���е��̶߳���������ˡ�ɾ����ʱ��¼���ļ�");
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
