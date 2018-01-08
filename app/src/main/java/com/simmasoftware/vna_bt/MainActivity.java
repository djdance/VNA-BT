/*
    This is an example application for transferring messages to and from the
    Simma Software VNA-Bluetooth. This application is meant only as an example
    of how to perform the basic required operations.
*/

package com.simmasoftware.vna_bt;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "djd";
    TextView mReception;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 1;
    private static final int REQUEST_ACTION_REQUEST_ENABLE_AND_CONNECT = 2;
    private static final int REQUEST_ACTION_REQUEST_ENABLE=3;
    private static final int REQUEST_ACTION_REQUEST_ENABLE_AND_SCAN=4;
    private static final UUID sppUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Write Characteristic: F8287520-BD28-11E4-B64D-0002A5D5C51B
    private static final byte RS232_FLAG = (byte) 0xC0;
    private static final byte RS232_ESCAPE = (byte) 0xDB;
    private static final byte RS232_ESCAPE_FLAG = (byte) 0xDC;
    private static final byte RS232_ESCAPE_ESCAPE = (byte) 0xDD;
    private static final String DEGREE  = " \u00b0F";
    private static final int ACK = 0;
    private static final int FA_J1939 = 1;
    private static final int FD_J1939 = 2;
    private static final int FA_J1708 = 3;
    private static final int FD_J1708 = 4;
    private static final int TX_J1939 = 5;
    private static final int RX_J1939 = 6;
    private static final int TX_J1708 = 8;
    private static final int RX_J1708 = 9;
    private static final int STATS = 23;
    private static final double KM_TO_MI = 0.621371;
    private static final double L_TO_GAL = 0.264172;
    private static final double KPA_TO_PSI = 0.145037738;
    private static final double KW_TO_HP = 1.34102209;
    private static final Integer MAX_16 = 0xffff;
    private static final Integer MAX_32 = 0xffffffff;
    private static final Integer MAX_8 = 0xff;
    private MenuItem connect_button = null;
    private boolean connected;
    private BluetoothSocket bluetoothSocket;
    BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
    private byte[] m_buffer;
    private int m_count;
    private boolean isInvalid;
    private boolean isStuffed;
    private int m_size;
    private HashMap<String, String> newData;
    private HashMap<String, Integer> monitorFields;
    int currDest=-1,unsentDest=-1;
    SharedPreferences prefs_default;
    String address="", addressName="";
    SimpleDateFormat mSDF = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    int counter_J1708_received=0, counter_J1708_sent=0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "MainActivity started, SDK=" + Build.VERSION.SDK_INT);
        prefs_default = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                //Log.d(TAG, "exception: " + this.getClass().getName() + ", " + throwable.toString());
                prefs_default.edit().putBoolean("crashed",true).apply();
                final Writer result = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(result);
                throwable.printStackTrace(printWriter);
                String stacktrace = "VNA-BT: " + result.toString();
                Log.e(TAG, "exception: " + stacktrace);
                Intent intent = new Intent(getApplicationContext(), checkService.class);
                intent.putExtra("postlog", stacktrace);
                startService(intent);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.exit(0);
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                }).start();// */
                //System.exit(0);
                //android.os.Process.killProcess(android.os.Process.myPid());
            }
        });
        setContentView(R.layout.activity_main);
        address=prefs_default.getString("address","");
        addressName=prefs_default.getString("addressName","");
        ((TextView) findViewById(R.id.addressName)).setText("with "+addressName);

        String versionName = "";
        try {
            versionName = " ver."+getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName+"";
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Exception Version Name: " + e.getLocalizedMessage());
        }
        setTitle(getTitle().toString()+versionName);

        mReception = (TextView) findViewById(R.id.EditTextReception);
        mReception.requestFocus();
        mReception.setMovementMethod(new ScrollingMovementMethod());
        log("App started");
        boolean restoredAfterCrash=prefs_default.getBoolean("crashed",false);
        if (restoredAfterCrash) {
            log("Restored after crash. Logs will be sent from "+postlog.crashlogsfilename);
            prefs_default.edit().putBoolean("crashed",false).apply();
        }
        //if (!addressName.equals(""))
        //    log("Last known device: "+addressName);




        ((Button) findViewById(R.id.button_send)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //1.14 Run Number
                mReception.requestFocus();
                sendbuttonWork(0);
            }
        });
        ((Button) findViewById(R.id.button_sendAll)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //1.14 Bruteforce Run Number
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int i=0;i<=20;i++) {
                            final int finalI = i;
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    sendbuttonWork(finalI);
                                }
                            });
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
            }
        });
        ((CheckBox) findViewById(R.id.startconnectCheckbox)).setEnabled(!address.equals(""));
        ((CheckBox) findViewById(R.id.startconnectCheckbox)).setChecked(prefs_default.getBoolean("startconnectCheckbox",false));
        ((CheckBox) findViewById(R.id.startconnectCheckbox)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs_default.edit().putBoolean("startconnectCheckbox",isChecked).commit();
            }
        });
        ((ToggleButton) findViewById(R.id.button_sendExample)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                toggleWork(0,isChecked);
            }
        });
        ((Button) findViewById(R.id.button_sendrs232)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleWork(1,true);
            }
        });
        ((Button) findViewById(R.id.button_sendDest1)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendbuttonWorkDest(1);
            }
        });
        ((Button) findViewById(R.id.button_sendDest2)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendbuttonWorkDest(6);
            }
        });
        ((Button) findViewById(R.id.button_sendDest3)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendbuttonWorkDest(7);
            }
        });
        ((Button) findViewById(R.id.button_sendDest4)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendbuttonWorkDest(9);
            }
        });
        ((Button) findViewById(R.id.button_sendDestPlus)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendbuttonWork(+1);
            }
        });
        ((Button) findViewById(R.id.button_sendDestMinus)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendbuttonWork(-1);
            }
        });
        ((Button) findViewById(R.id.button_sendDestLast)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendbuttonWork(0);
            }
        });
        initTextViews();//no need

        if (!restoredAfterCrash) {
            //String ss = null; Log.d(TAG, ss.substring(5));//emulate crash
            checkAndEnableBT(((CheckBox) findViewById(R.id.startconnectCheckbox)).isChecked() ? REQUEST_ACTION_REQUEST_ENABLE_AND_CONNECT : REQUEST_ACTION_REQUEST_ENABLE);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                (new postlog(MainActivity.this)).sendlogs();
            }
        }).start();
    }
    boolean checkAndEnableBT(int REQUEST_ACTION){
        boolean enabled=bluetooth.isEnabled();
        if (!enabled) {
            Toast.makeText(getApplicationContext(),"Turning ON Bluetooth", Toast.LENGTH_LONG);
            log("Turning ON Bluetooth");
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ACTION);
        }
        if (enabled && REQUEST_ACTION==REQUEST_ACTION_REQUEST_ENABLE_AND_CONNECT){
            log("Bluetooth is active");
            autoConnect();
        }
        return enabled;
    }
    void autoConnect(){
        log("Will auto connect on start...");
        Thread connectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                connectDevice(address, 0);
            }
        });
        connectThread.start();
    }
    void log(String text){
        log(0,text);
    }
    void log(boolean sent, String text){
        log(sent?1:-1,text);
    }
    void log(final int sent, final String text){
        runOnUiThread(new Runnable() {
            public void run() {
                String time111 = mSDF.format(new Date());
                String ss=time111+" "+(sent!=0?(sent>0?"sent":"rcvd")+":":"")+" "+text;
                mReception.append(ss+"\n");
                if (mReception.length() > 2000)
                    mReception.setText(mReception.getText().toString().substring(mReception.length() - 2000));
                Log.d(TAG,ss);
            }
        });
    }
    void log(final boolean sent, final byte[] text, final int len){
        runOnUiThread(new Runnable() {
            public void run() {
                String ss=(sent?"sent":"rcvd")+": ";
                for (int i = 0; i < len; i++) {
                    String h=Integer.toHexString((int)(text[i] & 0xFF));
                    if (h.length()==1)
                        h="0"+h;
                    ss = ss+h+" ";
                }
                //ss+=" = "+(new String(text));
                mReception.append(ss+"\n");
                if (mReception.length() > 2000)
                    mReception.setText(mReception.getText().toString().substring(mReception.length() - 2000));
                Log.d(TAG,ss);
            }
        });
    }
    void log(final boolean sent, final byte[] text){
        log(sent,text,text.length);
    }
    @Override
    protected void onPause() {
        if (mHandler!=null) {
            mHandler.removeMessages(1);
            mHandler.removeMessages(2);
        }
        super.onPause();
    }
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                //send to Dest and repeat if need
                if (connected) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            sendbuttonWorkDest(currDest);
                        }
                    });
                    if (((CheckBox) findViewById(R.id.repeatCheckbox)).isChecked()) {
                        int delay = 0;
                        try {
                            delay = Integer.parseInt(((EditText) findViewById(R.id.repeatEditText)).getText().toString());
                        } catch (Exception e) {
                        }
                        if (delay <= 0)
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    ((CheckBox) findViewById(R.id.repeatCheckbox)).setChecked(false);
                                }
                            });
                        else
                            mHandler.sendMessageDelayed(Message.obtain(mHandler, 1, ""), 1000 * delay);
                    }
                } else {
                    log("Not connected yet");
                    if (((CheckBox) findViewById(R.id.startconnectCheckbox)).isChecked()){
                        unsentDest=currDest;
                        Thread reconnectThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                reconnect();
                            }
                        });
                        reconnectThread.start();
                    }
                }
            } else if (msg.what==2) {
                //send ping
                if (connected) {
                    toggleWork(2, false);
                    if (((CheckBox) findViewById(R.id.pingCheckbox)).isChecked()) {
                        int delay = 0;
                        try {
                            delay = Integer.parseInt(((EditText) findViewById(R.id.pingEditText)).getText().toString());
                        } catch (Exception e) {
                        }
                        if (delay <= 0)
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    ((CheckBox) findViewById(R.id.pingCheckbox)).setChecked(false);
                                }
                            });
                        else
                            mHandler.sendMessageDelayed(Message.obtain(mHandler, 2, ""), 1000 * delay);
                    }
                }
            } else if (msg.what>=3 && msg.what<=5){
                connectDevice(address, msg.what-2);
            } else {
                Log.d(TAG,"handle wrong msg "+msg.what);
            }
        }
    };

    void sendbuttonWork(int increment){
        if (currDest+increment>=0)
            currDest+=increment;
        ((TextView) findViewById(R.id.curDestTV)).setText(""+currDest);
        mHandler.sendMessageDelayed(Message.obtain(mHandler, 1, ""), 50);
        //mHandler.sendMessageDelayed(Message.obtain(mHandler, 2, ""), 50);//debug!! remove in prod
    }
    void sendbuttonWorkDest(int dest){
        //get number of stored message
        //increase to 4 digits
        Log.d(TAG,"sendbuttonWorkDest "+dest);
        String t=Integer.toHexString(dest);
        if (t.length()==1)
            t="000"+t;
        else if (t.length()==2)
            t="00"+t;
        else if (t.length()==3)
            t="0"+t;

        byte[] message = new byte[14];
        byte[] stuffed = new byte[28];
        int cnt;

        //j1708
        message[3] = (byte) 0xBC;//MID
        message[4] = (byte) 0xFF;//PID
        message[5] = (byte) 0xF5;//PID
        message[6] = (byte) 0x01;//priority, will be gone to the end after checksum
        message[7] = (byte) 0x05;//len without this checksum
        message[8] = (byte) 0x44;//D
        message[9] = (byte) t.charAt(0);// (byte) 0x30;//0
        message[10] = (byte) t.charAt(1);// (byte) 0x30;//0
        message[11] = (byte) t.charAt(2);// (byte) 0x30;//0
        message[12] = (byte) t.charAt(3);// (byte) (0x30+dest);//1+dest
        //byte chk=(byte)cksum(message);
        ////message[13] = chk;//checksum NO NEED for VNA! it add J1708 checksum itself! but leave it for rs485!

        message[0] = 0;
        message[1] = 12;//length with checksum
        message[2] = (byte) TX_J1708; //VNA_MSG_TX_J1587

        message[13] = (byte) cksum(message);

        // Tack on beginning of string marker
        stuffed[0] = RS232_FLAG;
        int esc_cnt = 1;
        // Bytestuff
        for( cnt = 0; cnt < message.length; cnt++ ) {
            if( message[cnt] == RS232_FLAG ) {
                stuffed[cnt+esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt+esc_cnt] = RS232_ESCAPE_FLAG;
            } else if( message[cnt] == RS232_ESCAPE ) {
                stuffed[cnt+esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt+esc_cnt] = RS232_ESCAPE_ESCAPE;
            } else{
                stuffed[cnt+esc_cnt] = message[cnt];
            }
        }// */
        //log("Will sendCommand, length="+(cnt+esc_cnt));//+", chk="+chk);
        sendCommand(new TxStruct(stuffed, cnt+esc_cnt));
        counter_J1708_sent++;
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateLabels();
            }
        });
    }
    void toggleWork(int casse, boolean isChecked){
        byte[] message=new byte[0];
        if (casse==1) {
            //toggle rs232
            message = new byte[4];
            message[0] = 0;
            message[1] = 2;//length
            message[2] = 35;
            message[3] = (byte) cksum(message);
        }else if (casse==0) {
            //toggle VNA BT reporrt
            message = new byte[8];
            message[0] = 0;
            message[1] = 6;//length
            message[2] = 64;//23;//stat (page 35)
            message[3] = 0;
            message[4] = 0;
            message[5] = 0;
            message[6] = (byte) (isChecked ? 0x00 : 0x05);//toggle 1-sec report ODO and stat
            message[7] = (byte) cksum(message);
        }else if (casse==2) {
            //ping
            message = new byte[5];
            message[0] = 0;
            message[1] = 3;//length
            message[2] = ACK;
            message[3] = 1;
            message[4] = (byte) cksum(message);
        }
        // Tack on beginning of string marker
        byte[] stuffed = new byte[17];
        stuffed[0] = RS232_FLAG;
        int cnt;
        int esc_cnt = 1;
        // Bytestuff
        for (cnt = 0; cnt < message.length; cnt++) {
            if (message[cnt] == RS232_FLAG) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_FLAG;
            } else if (message[cnt] == RS232_ESCAPE) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_ESCAPE;
            } else {
                stuffed[cnt + esc_cnt] = message[cnt];
            }
        }// */
        //log(true,"Will sendCommand, length="+(cnt+esc_cnt));//+", chk="+chk);
        sendCommand(new TxStruct(stuffed, cnt+esc_cnt));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_connect) {
            connect_button = item;
            menuConnectClick();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void menuConnectClick(){
        if (checkAndEnableBT(REQUEST_ACTION_REQUEST_ENABLE_AND_SCAN)) {
            if (connect_button.getTitle().toString().compareToIgnoreCase("Connect") == 0) {
                // do BT connect
                connect_button.setTitle("Connecting...");
                log("Connecting...");
                Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            } else {
                disconnect();
            }
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode)
        {
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a bluetoothDevice to connect
                if (resultCode == Activity.RESULT_OK) {
                    address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    addressName = data.getExtras().getString("addressName");
                    prefs_default.edit().putString("address",address).commit();
                    prefs_default.edit().putString("addressName",addressName).commit();
                    ((CheckBox) findViewById(R.id.startconnectCheckbox)).setEnabled(!address.equals(""));
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((TextView) findViewById(R.id.addressName)).setText("with "+addressName);
                        }
                    });
                    Thread connectThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            connectDevice(address, 0);
                        }
                    });
                    connectThread.start();
                } else {
                    log("Failed to request BT device");
                    if(connect_button != null) connect_button.setTitle("Connect");
                }
                break;
            case REQUEST_ACTION_REQUEST_ENABLE:
            case REQUEST_ACTION_REQUEST_ENABLE_AND_CONNECT:
            case REQUEST_ACTION_REQUEST_ENABLE_AND_SCAN:
                //turning ON bt
                //Log.d(TAG,"onActivityResult: for REQUEST_ACTION_REQUEST_ENABLE_AND_CONNECT: resultCode="+resultCode);
                if (resultCode == Activity.RESULT_OK){
                    if (requestCode==REQUEST_ACTION_REQUEST_ENABLE_AND_CONNECT){
                        log("Bluetooth is active");
                        autoConnect();
                    }
                    if (requestCode==REQUEST_ACTION_REQUEST_ENABLE_AND_SCAN){
                        menuConnectClick();
                    }
                }else {
                    log("Failed to turn on BT");
                }
                break;
        }
    }

    private void initTextViews() {
        newData = new HashMap<>();
        monitorFields = new HashMap<>();
        newData.put("RPM", "");
        monitorFields.put("RPM", R.id.RPMField);
        newData.put("Coolant", "");
        monitorFields.put("Coolant", R.id.CoolantTempField);
        newData.put("Oil Pressure", "");
        monitorFields.put("Oil Pressure", R.id.OilPressureField);
        newData.put("Frames","");
        monitorFields.put("Frames", R.id.CANFramesField);
    }

    private final Runnable readRun = new Runnable()
    {
        public void run()
        {
            receiveDataFromBT(bluetoothSocket);
        }
    };
    private Thread readThread;

    private BluetoothSocket connectDevice(final String address, int i) {
        // Get the BluetoothDevice object

        connected = false;
        try{
            BluetoothDevice bluetoothDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address);
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(sppUUID);
            bluetoothSocket.connect();

            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Connected!", Toast.LENGTH_SHORT).show();
                    log("Connected with "+addressName);
                    if (connect_button != null) connect_button.setTitle("Disconnect");
                    newData.put("Frames", "-");
                    updateLabels();
                }
            });
            connected = true;
            //djd init_j1939();
            if(readThread != null && readThread.isAlive()) {
                readThread.interrupt();
                while(readThread.isAlive()) Thread.yield();
            } else {
                readThread = new Thread(readRun);
                readThread.setPriority(4);
                readThread.start();
            }
            if (unsentDest>=0) {
                currDest=unsentDest;
                unsentDest=-1;
                mHandler.sendMessageDelayed(Message.obtain(mHandler, 1, ""), 100);
            }

        } catch (Exception ioex){
            log("BT error: "+ioex.toString().substring(20));
        }

        if (!connected){
            if(i<3){
                log("will try one more time...("+i+")");
                mHandler.sendMessageDelayed(Message.obtain(mHandler, 3+i, ""), 1000 );
            } else {
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Bluetooth connection error", Toast.LENGTH_SHORT).show();
                        log("Failed "+addressName+", is it active?");
                        if (connect_button != null) connect_button.setTitle("Connect");
                        disconnect();
                    }
                });
            }
        }

        return (bluetoothSocket);
    }


    private void reconnect() {
        log("Bluetooth reconnection...");
        if(bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) { }
        }
        connectDevice(address, 1);
    }

    private void disconnect() {
        try {
            if(readThread != null) readThread.interrupt();
            if (bluetoothSocket != null)
            {
                bluetoothSocket.close();
                bluetoothSocket = null;
            }
            mHandler.removeMessages(2);
        } catch (IOException e) {
            /* We don't really care about the reconnect exceptions */
            //Log.e(TAG, "In reconnect", e);
        }
        if (connected)
            log("Disconnected");
        connected = false;
        if(connect_button != null) connect_button.setTitle("Connect");
    }

    private void receiveDataFromBT(BluetoothSocket socket) {
        try {
            byte[] buffer = new byte[1024];
            int buf_len = 0;


            if (socket == null) {
                return;
            }

            InputStream inputStream = socket.getInputStream();

            while (true) {
                try {
                    if(Thread.interrupted()) {
                        inputStream.close();
                        return;
                    }
                    // Read from the InputStream
                    if (inputStream != null && socket != null) {
                        buf_len = inputStream.read(buffer);
                    }
                    Thread.sleep(1);

                    if (buf_len == -1) {
                        inputStream.close();
                        break;
                    }
                    parseMessage(buffer, buf_len);

                } catch (IOException e) {
                    if(Thread.interrupted()) {
                        inputStream.close();
                        return;
                    }
                    reconnect();
                    break;
                } catch (InterruptedException e) {
                    inputStream.close();
                    Log.e(TAG,"Interrupted read",e);
                    return;
                }
            }

        } catch (IOException e) {
            Log.e(TAG,"", e);
        }
    }

    private void parseMessage(byte[] buf, int len) {
        log(false,buf,len);
        for (int i = 0; i < len; i++) {
            processCharFromBus(buf[i]);
        }
    }

    private void processCharFromBus(byte val) {
        try {
            //Is it the start of the message?
            if (val == RS232_FLAG) {
                isInvalid = false;
                isStuffed = false;
                m_size = -1;
                m_count = 0;
            } else if (!isInvalid) {
                if (val == RS232_ESCAPE) {
                    isStuffed = true;
                } else {
                    //If previous byte was an escape, then decode current byte
                    if (isStuffed) {
                        isStuffed = false;
                        if (val == RS232_ESCAPE_FLAG) {
                            val = RS232_FLAG;
                        } else if (val == RS232_ESCAPE_ESCAPE) {
                            val = RS232_ESCAPE;
                        } else {
                            isInvalid = true;
                            // Invalid byte after escape, must abort
                            return;
                        }
                    }
                    //At this point data is always un-stuffed
                    if (m_count < m_buffer.length) {
                        m_buffer[m_count] = val;
                        m_count++;
                    } else {
                        //Full buffer
                    }

                    //At 2 bytes, we have enough info to calculate a real message length
                    if (m_count == 2) {
                        m_size = ((m_buffer[0] << 8) | m_buffer[1]) + 2;
                    }

                    //Have we received the entire message? If so, is it valid?
                    if (m_count == m_size)
                        if (val == cksum(m_buffer, m_count -1)) {
                            m_count--; //Ignore the checksum at the end of the message
                            processPacket(m_buffer);
                        } else {
                            log("Received data is invalid");
                        }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getStackTrace()[0]);
        }
    }

    private void processPacket(byte[] packet) {
        int msgID = packet[2];
        if (msgID == ACK) {
            int id=packet[3];
            if (id==TX_J1708){
                counter_J1708_received++;
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateLabels();
                    }
                });
            }
        } else if (msgID == RX_J1939) {
            final Integer pgn = ((packet[4] & 0xFF) << 16) | ((packet[5] & 0xFF) << 8) | (packet[6] & 0xFF);
            Double d;
            Integer i;
            String out;
            switch (pgn) {
                case 61444:
                    i = ((packet[14] & 0xFF) << 8) | (packet[13] & 0xFF);
                    if(i.equals(MAX_16)) break;
                    newData.put("RPM", (i * 0.125 + "")); /* SPN 190 */
                    break;
                case 65262:
                    i = (packet[10] & 0xFF);
                    if(i.equals(MAX_8)) break;
                    d = (i - 40) * 9 / 5.0 + 32;
                    out = String.format("%.1f%s",d,DEGREE);
                    newData.put("Coolant",out); /* SPN 110 */
                    break;
                case 65263:
                    i = (packet[13] & 0xFF);
                    if(i.equals(MAX_8)) break;
                    d = i * 4 * KPA_TO_PSI;
                    out = String.format("%.2f psi",d);
                    newData.put("Oil Pressure", out); /* SPN 100 */
                    break;
            }
        } else if(msgID == STATS) {
            byte a = packet[11];
            byte b = packet[12];
            byte c = packet[13];
            byte d = packet[14];
            Long canFramesCount = (long) (((a & 0xFF) << 24) | ((b & 0xFF) << 16) | ((c & 0xFF) << 8) | (d & 0xFF));
            newData.put("Frames", canFramesCount + " frames");
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateLabels();
                }
            });
        }
    }

    private void updateLabels() {
        ((TextView) findViewById(R.id.lostCounter)).setText("Lost "+(100-Math.round(100*counter_J1708_received/counter_J1708_sent))+"%");
        /*
        for(Fragment f : getSupportFragmentManager().getFragments()) {
            if(f != null && f.getClass().equals(MainActivityFragment.class)) {
                for (Map.Entry<String, Integer> entry : monitorFields.entrySet()) {
                    Integer tv = entry.getValue();
                    String label = newData.get(entry.getKey());
                    if (!label.equals("")) {
                        if (tv != null) {
                            ((MainActivityFragment)f).update(tv, label);
                        }
                    }
                }
            }
        }*/
    }

    private void sendCommand(TxStruct command) {
        String prefix = "";
        log(true,command.getBuf(),Math.min(command.getLen(),20));
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.getOutputStream().write(command.getBuf(),0,command.getLen());
            }catch (IOException e){
                log("error! BT output socket closed");
                if (((CheckBox) findViewById(R.id.reconnectCheckbox)).isChecked()) {
                    log("Will try to auto reconnect...");
                    //reconnect();//crash
                    Thread connectThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            connectDevice(address, 0);
                        }
                    });
                    connectThread.start();
                }
            }
        } else {
            log("error! No BT connected");
            log("PLEASE REPORT IN THIS CASE! Will add BT reconnecting and here!");
            disconnect();
        }
    }


    private int cksum(byte[] commandBytes)
    {
        int count = 0;

        for (int i = 1; i < commandBytes.length; i++)
        {
            count += uByte(commandBytes[i]);
        }

        return (byte) (~(count & 0xFF) + (byte) 1);
    }

    private int cksum(byte[] data, int numbytes) {
        int count = 0;

        for (int i = 0; i < numbytes; i++) {
            count += uByte(data[i]);
        }
        return (byte) (~(count & 0xFF) + (byte) 1);
    }

    private int uByte(byte b)
    {
        return (int)b & 0xFF;
    }

    private void init_j1939()
    {
        m_buffer = new byte[4096];
        m_count = 0;
        // trimUntil = 0;
        long[] initPGN_AddFilter = {61444, 65262, 65263};
        /*
PGN (Parameter Group Number) — номер группы параметров, определяющий содер-
жимое соответствующего сообщения шины CAN согласно SAE J1939. Термин PGN ис-
пользуется для обозначения сообщений шины CAN.
         */

        for(long pgn:initPGN_AddFilter)
        {
            sendCommand(filterAddDelJ1939((byte) 0, pgn, true));
        }
    }

    public TxStruct filterAddDelJ1939(byte port, long pgnLong, boolean add)
    {
        byte[] pgn = new byte[3];

        pgn[0] = (byte) ((pgnLong >> 16) & 0xFF);
        pgn[1] = (byte) ((pgnLong >> 8) & 0xFF);
        pgn[2] = (byte) ((pgnLong) & 0xFF);

        byte[] message = new byte[8];
        byte[] stuffed = new byte[17];
        int cnt;

        message[0] = 0;
        message[1] = 6;
        message[2] = (byte) (add ? FA_J1939 : FD_J1939);
        message[3] = port;
        System.arraycopy(pgn, 0, message, 4, 3);

        message[7] = (byte) cksum( message);


        // Tack on beginning of string marker

        stuffed[0] = RS232_FLAG;


        int esc_cnt = 1;

        // Bytestuff
        for( cnt = 0; cnt < 8; cnt++ )
        {
            if( message[cnt] == RS232_FLAG )
            {
                stuffed[cnt+esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt+esc_cnt] = RS232_ESCAPE_FLAG;
            }
            else if( message[cnt] == RS232_ESCAPE )
            {
                stuffed[cnt+esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt+esc_cnt] = RS232_ESCAPE_ESCAPE;
            }
            else
            {
                stuffed[cnt+esc_cnt] = message[cnt];
            }
        }
        return new TxStruct(stuffed, cnt+esc_cnt);
    }



    private TxStruct requestPGNJ1939(byte port, long pgnLong)
    {
        // c0 00 0a 05 00 pp gg nn 00 00 00 ff xx
        //                  PGN
        byte[] pgn = new byte[3];
        byte[] stuffed = new byte[30];

        pgn[0] = (byte) ((pgnLong) & 0xFF);
        pgn[1] = (byte) ((pgnLong >> 8) & 0xFF);
        pgn[2] = (byte) ((pgnLong >> 16) & 0xFF);

        byte[] message = new byte[14];
        int cnt;

        message[0] = 0;
        message[1] = (byte) (message.length - 2);
        message[2] = TX_J1939;
        message[3] = port;
        System.arraycopy(new byte[]{(byte) 0x00, (byte) 0xEA, (byte) 0x00}, 0, message, 4, 3);

        message[7] = (byte) 255; 	// destination addr
        message[8] = (byte) 252;				// source addr
        message[9] = 6;				// priority

        System.arraycopy(pgn, 0, message, 10, 3);

        message[13]	= (byte) cksum(message);

        // Tack on beginning of string marker
        stuffed[0] = RS232_FLAG;
        int esc_cnt = 1;
        // bytestuff
        for (cnt = 0; cnt < message.length; cnt++) {
            if (message[cnt] == RS232_FLAG) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_FLAG;
            } else if (message[cnt] == RS232_ESCAPE) {
                stuffed[cnt + esc_cnt] = RS232_ESCAPE;
                esc_cnt++;
                stuffed[cnt + esc_cnt] = RS232_ESCAPE_ESCAPE;
            } else {
                stuffed[cnt + esc_cnt] = message[cnt];
            }
        }

        return new TxStruct(stuffed, cnt+esc_cnt);
    }

    class TxStruct {
        private byte[] buf;
        private int len;

        public TxStruct() {
            buf = new byte[10];
            len = 0;
        }

        public TxStruct(int bufSize, int len) {
            buf = new byte[bufSize];
            this.len = len;
        }

        public TxStruct(byte[] buf, int len) {
            this.buf = buf;
            this.len = len;
        }

        public void setLen(int length) {
            len = length;
        }

        public int getLen() {
            return len;
        }

        public void setBuf(int pos, byte data) {
            if( pos >= 0 && pos < buf.length ) buf[pos] = data;
        }

        public byte[] getBuf() {
            return buf;
        }
    }



}
