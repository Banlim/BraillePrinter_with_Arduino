package com.example.jeongsanga.braille_app2;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.preference.DialogPreference;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.view.Menu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


/**
 * Created by Jeong Sang A on 2017-11-14.
 */

public class SubActivity extends AppCompatActivity {


    static final int REQUEST_ENABLE_BT = 10;
    BluetoothAdapter mBluetoothAdapter;
    int mPairedDeviceCount = 0;
    Set<BluetoothDevice> mDevices;
    BluetoothDevice mRemoteDevice;
    BluetoothSocket mSocket = null;
    OutputStream mOutputStream = null;
    InputStream mInputStream = null;

    Thread mWorkerThread = null;
    String mStrDelimiter = "\n";
    char mCharDelimiter = '\n';
    byte[] readBuffer;
    int readBufferPosition;

    Button mButtonSend;
    EditText mEditSend, mEditReceive;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub);

        mButtonSend = (Button)findViewById(R.id.sendButton);
        mEditSend = (EditText)findViewById(R.id.sendString);
        mEditReceive = (EditText)findViewById(R.id.receiveString);

        mButtonSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData(mEditSend.getText().toString());
                mEditSend.setText("");
            }
        });
        checkBluetooth();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (requestCode == RESULT_OK) {
                    selectDevice();
                } else if (requestCode == RESULT_CANCELED) {
                    Toast.makeText(getApplicationContext(), "블루투스를 사용할 수 없어 프로그램을 종료합니다.", Toast.LENGTH_LONG).show();
                    finish();
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }



    void checkBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "기기가 블루투스를 지원하지 않습니다.", Toast.LENGTH_LONG).show();
            finish();
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Toast.makeText(getApplicationContext(), "현재 블루투스가 비활성 상태입니다.", Toast.LENGTH_LONG).show();

                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                selectDevice();
            }
        }
    }


    void selectDevice() {

        mDevices = mBluetoothAdapter.getBondedDevices();
        mPairedDeviceCount = mDevices.size();

        if (mPairedDeviceCount == 0) {
            Toast.makeText(getApplicationContext(), "페이링된 장치 없음", Toast.LENGTH_LONG).show();
            finish();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("블루투스 장치 선택");

        //페어링 된 블루투스 장치 이름 목록 작성

        List<String> listItems = new ArrayList<String>();
        for (BluetoothDevice device : mDevices) {
            listItems.add(device.getName());
        }
        listItems.add("취소");

        final CharSequence[] items = listItems.toArray(new CharSequence[listItems.size()]);

        final int finalMPairedDeviceCount = mPairedDeviceCount;
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                if (item == finalMPairedDeviceCount) {
                    Toast.makeText(getApplicationContext(), "연결할 장치를 선택하지 않았습니다.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    //연결할 장치를 선택한 경우 선택한 장치와 연결 시도
                    connectToSelectedDevice(items[item].toString());
                }
            }

        });

        builder.setCancelable(false); //뒤로 가기 금지
        AlertDialog alert = builder.create();

        alert.show();
    }

    void beginListenForData() {
        final Handler handler = new Handler();

        readBuffer = new byte[1024];
        readBufferPosition = 0;

        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        int bytesAvailable = mInputStream.available(); // 수신 데이터 확인
                        if (bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mInputStream.read(packetBytes);

                            for (int i = 0; i < bytesAvailable; i++) {
                                byte b = packetBytes[i];

                                if (b == mCharDelimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);

                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            mEditReceive.setText(mEditReceive.getText().toString() + data + mStrDelimiter);
                                        }
                                    });
                                } else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    } catch (IOException ex) {
                        Toast.makeText(getApplicationContext(), "데이터 수신 중 오류 발생", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            }
        });

        mWorkerThread.start();
    }


    void sendData(String msg) {
        msg += mStrDelimiter;
        try {
            mOutputStream.write(msg.getBytes());
        } catch (Exception e) {
            // 문자열 전송 도중 오류 발생한 경우
            Toast.makeText(getApplicationContext(), "데이터 전송 중 오류 발생", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    BluetoothDevice getDeviceFromBondedList(String name) {
        BluetoothDevice selectedDevice = null;

        for (BluetoothDevice device : mDevices) {
            if (name.equals(device.getName())) {
                selectedDevice = device;
                break;
            }
        }
        return selectedDevice;
    }

    @Override
    protected void onDestroy() {

        try{
            mWorkerThread.interrupt();
            mInputStream.close();
            mOutputStream.close();
            mSocket.close();
        }
        catch (Exception e){}

        super.onDestroy();
    }

    void connectToSelectedDevice(String selectedDeviceName){
        mRemoteDevice = getDeviceFromBondedList(selectedDeviceName);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        try{
            mSocket = mRemoteDevice.createRfcommSocketToServiceRecord(uuid);
            mSocket.connect();

            mOutputStream = mSocket.getOutputStream();
            mInputStream = mSocket.getInputStream();

            beginListenForData();
        } catch (Exception e){
            Toast.makeText(getApplicationContext(),"블루투스 연결 중 오류 발생", Toast.LENGTH_LONG).show();
            finish();
        }
    }


}






