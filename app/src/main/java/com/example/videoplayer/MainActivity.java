package com.example.videoplayer;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.VideoView;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    // 使用常量作为本程序的多点广播IP地址
    private static final String BROADCAST_IP = "230.0.0.1";
    // 使用常量作为本程序的多点广播目的的端口
    public static final int BROADCAST_PORT = 4567;
    // 定义每个数据报的最大大小为4K
    private static final int DATA_LEN = 4096;

    // 定义本程序的MulticastSocket实例
    private MulticastSocket socket = null;
    private InetAddress broadcastAddress = null;
    // 定义接收网络数据的字节数组
    byte[] inBuff = new byte[DATA_LEN];
    // 以指定字节数组创建准备接受数据的DatagramPacket对象
    private DatagramPacket inPacket = new DatagramPacket(inBuff, inBuff.length);
    // 定义一个用于发送的DatagramPacket对象
    private DatagramPacket outPacket = null;
    Button playButton;
    VideoView videoView;
    EditText rtspUrl;
    RadioButton radioStream;
    RadioButton radioFile;
   public Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    if (videoView.getCurrentPosition() >= seekBar.getMax()) {
                        stopTimer();
                        System.out.println("---------------------stoped");
                    } else {
                        System.out.println(videoView.getCurrentPosition() + "-------------" + seekBar.getProgress());
                        seekBar.setProgress(videoView.getCurrentPosition());
                    }
                    break;
            }
        }
    };
    private String name;

    private void stopTimer() {
        if (timer != null && timerTask != null) {
            timer.cancel();
            timer = null;
            timerTask.cancel();
            timerTask = null;
        }
    }

    private SeekBar seekBar;
    private Timer timer;
    private TimerTask timerTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rtspUrl = (EditText) this.findViewById(R.id.url);
        playButton = (Button) this.findViewById(R.id.start_play);
        radioStream = (RadioButton) this.findViewById(R.id.radioButtonStream);
        radioFile = (RadioButton) this.findViewById(R.id.radioButtonFile);
        seekBar = (SeekBar) this.findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

                videoView.seekTo(seekBar.getProgress());
            }
        });
        playButton.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                if (radioStream.isChecked()) {
                    PlayRtspStream(rtspUrl.getEditableText().toString());
                } else if (radioFile.isChecked()) {
                    PlayLocalFile(rtspUrl.getEditableText().toString());
                }
            }
        });

        videoView = (VideoView) this.findViewById(R.id.rtsp_player);
        initSocket();

    }

    private void initSocket() {
        try {
            socket = new MulticastSocket(4567);
            broadcastAddress = InetAddress.getByName(BROADCAST_IP);
            // 将该socket加入指定的多点广播地址
            socket.joinGroup(broadcastAddress);
            // 设置本MulticastSocket发送的数据报被回送到自身
            socket.setLoopbackMode(true);
            // 初始化发送用的DatagramSocket，它包含一个长度为0的字节数组
            outPacket = new DatagramPacket(new byte[0], 0, broadcastAddress,
                    BROADCAST_PORT);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("------init erere");
        }
        open();
    }

    public void open() {
        new Thread(){
            @Override
            public void run() {
                super.run();
                while (true) {
                    // 读取Socket中的数据，读到的数据放在inPacket所封装的字节数组里。
                    try {
                        System.out.println("-----------------receive ");
                        socket.receive(inPacket);
                        String videoUrl= new String(inBuff, 0, inPacket.getLength());
                        System.out.println("聊天信息："
                                +videoUrl);
                        showDialogDIY(videoUrl);
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.out.println("---------------------receive error");
                    }
                    // 打印输出从socket中读取的内容

                }
            }
        }.start();
    }

    private void showDialogDIY(final String videoUrl) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Dialog alertDialog = new AlertDialog.Builder(MainActivity.this).
                        setTitle("视频入侵").
                        setMessage("查看吗？").
                        setPositiveButton("查看", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // TODO Auto-generated method stub
                                dialog.dismiss();
                                PlayRtspStream(videoUrl);

                            }
                        }).
                        setNegativeButton("取消", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // TODO Auto-generated method stub
                                dialog.dismiss();
                            }
                        }).
                        create();
                alertDialog.show();
            }
        });

    }

    public void send(String url) {
        byte[] buff = url.getBytes();
        // 设置发送用的DatagramPacket里的字节数据
        outPacket.setData(buff);
        // 发送数据报
        new Thread(){
            public void run() {
                try {
                    socket.send(outPacket);
                    System.out.println("------send");
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("---------send error");
                }
            }
        }.start();



    }

    //play rtsp stream
    private void PlayRtspStream(String rtspUrl) {
        videoView.setVideoURI(Uri.parse(rtspUrl));
        videoView.requestFocus();
        videoView.start();
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                startTimer();
            }
        });

    }

    private void startTimer() {
        if (timer == null && timerTask == null) {
            timer = new Timer();

            timerTask = new TimerTask() {
                @Override
                public void run() {
                    handler.sendEmptyMessage(0);
                }
            };
            seekBar.setMax(videoView.getDuration());
            System.out.println("--------------------vedio max" + videoView.getDuration());
            System.out.println("--------------------vedio po"+videoView.getCurrentPosition());
            timer.schedule(timerTask, 0, 1000);
        }

    }

    public void share(View v){
        name = rtspUrl.getText().toString();
        uploadWithHttpClient();
    }
    private boolean uploadWithHttpClient() {
        new Thread(){
            @Override
            public void run() {
                super.run();
                String url="http://172.16.80.1:8080/GoitGuess/UdpServlet";
                HttpClient client=new DefaultHttpClient();// 开启一个客户端 HTTP 请求
                HttpPost post = new HttpPost(url);//创建 HTTP POST 请求
                MultipartEntityBuilder builder = MultipartEntityBuilder.create();
//		builder.setCharset(Charset.forName("uft-8"));//设置请求的编码格式
                builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);//设置浏览器兼容模式
                int count=0;
                File file = new File(Environment.getExternalStorageDirectory(),
                        name);
                // FileBody fileBody = new FileBody(file);
                builder.addBinaryBody("file", file);
                //for (File file:files) {
//			FileBody fileBody = new FileBody(file);//把文件转换成流对象FileBody
//			builder.addPart("file"+count, fileBody);
                //  builder.addBinaryBody("file"+count, file);
                //     count++;
                //   }
                HttpEntity entity = builder.build();// 生成 HTTP POST 实体
                post.setEntity(entity);//设置请求参数
                HttpResponse response = null;// 发起请求 并返回请求的响应
                try {
                    response = client.execute(post);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("--------------http excute error");
                }
                if (response.getStatusLine().getStatusCode()==200) {
                    System.out.println("-----------200");
                    send("http://172.16.80.1:8080/GoitGuess/upload/"+name);
                }

            }
        }.start();
        return true;
    }
    //play rtsp stream
    private void PlayLocalFile(String filePath) {
        videoView.setVideoPath(Environment.getExternalStorageDirectory() + "/" + filePath);
        videoView.requestFocus();
        videoView.start();
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                startTimer();
            }
        });
    }
}