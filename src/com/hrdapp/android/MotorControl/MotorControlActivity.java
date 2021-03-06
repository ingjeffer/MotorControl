package com.hrdapp.android.MotorControl;

import java.lang.ref.WeakReference;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

public class MotorControlActivity extends Activity {

    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    private static final int DIALOG_YES_NO_MESSAGE = 0;
    
    private Timer   mTimer   = null;
    private Handler mTimerHandler = new Handler();
    
    // Layout Views
    private TextView mTitle;
    private MainView mView;

    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
//    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
//    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothMotorControlService mCmdSendService = null;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        mView = new MainView(this);
        setContentView(mView);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);

        // Set up the custom title
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    
    }
    @Override
    public void finish()
    {
        showDialog(DIALOG_YES_NO_MESSAGE);
    }

    public void appEnd()
    {
        super.finish();
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        switch(id)
        {
        case DIALOG_YES_NO_MESSAGE:
            return new AlertDialog.Builder(this)
                .setTitle("終了確認")
                .setMessage("アプリを終了しますか？")
                .setPositiveButton("OK", 
                    new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                            // アプリ終了
                            appEnd();
                        }
                    })
                .setNegativeButton("CANCEL", 
                    new DialogInterface.OnClickListener()
                    {
                        public void onClick(DialogInterface dialog, int whichButton)
                        {
                        }
                    })
                .create();
            default:
                break;
        }
        return null;
    }

    @Override
    public void onStart() {
        super.onStart();

        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mCmdSendService == null) setupBT();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mCmdSendService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mCmdSendService.getState() == BluetoothMotorControlService.STATE_NONE) {
              // Start the Bluetooth chat services
            	mCmdSendService.start();
            }
        }
    }

    public void starttimer(){
        if(mTimer == null){
            mTimer = new Timer(true);
            mTimer.schedule( new TimerTask(){
                @Override
                public void run() {
                    // mHandlerを通じてUI Threadへ処理をキューイング
                	mTimerHandler.post( new Runnable() {
                        public void run() {
                            mView.TimerSendCmd();     
                        }
                    });
                }
            }, 200, 200);
        }
    }
    public void stoptimer(){
        if(mTimer != null){
            mTimer.cancel();
            mTimer = null;
        }
    }

    private void setupBT() {

        // Initialize the BluetoothChatService to perform bluetooth connections
    	mCmdSendService = new BluetoothMotorControlService(this, mHandler);

    }

    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Stop the Bluetooth chat services
        if (mCmdSendService != null) mCmdSendService.stop();
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mCmdSendService.getState() != BluetoothMotorControlService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mCmdSendService.write(send);
        }
    }

    static class BtHandler extends Handler {
        private final WeakReference<MotorControlActivity> mActivity; 

        BtHandler(MotorControlActivity activity) {
        	mActivity = new WeakReference<MotorControlActivity>(activity);
        }
        @Override
        public void handleMessage(Message msg)
        {
        	MotorControlActivity activity = mActivity.get();
             if (activity != null) {
            	 activity.handleMessage(msg);
             }
        }
    }

    private final Handler mHandler = new BtHandler(this);

    public void handleMessage(Message msg) {
        switch (msg.what) {
        case MESSAGE_STATE_CHANGE:
            switch (msg.arg1) {
            case BluetoothMotorControlService.STATE_CONNECTED:
                mTitle.setText(R.string.title_connected_to);
                mTitle.append(mConnectedDeviceName);
                break;
            case BluetoothMotorControlService.STATE_CONNECTING:
                mTitle.setText(R.string.title_connecting);
                break;
            case BluetoothMotorControlService.STATE_LISTEN:
            case BluetoothMotorControlService.STATE_NONE:
                mTitle.setText(R.string.title_not_connected);
                break;
            }
            break;
        case MESSAGE_WRITE:
//                byte[] writeBuf = (byte[]) msg.obj;
            // construct a string from the buffer
//                String writeMessage = new String(writeBuf);
//                mConversationArrayAdapter.add("Me:  " + writeMessage);
            break;
        case MESSAGE_READ:
//                byte[] readBuf = (byte[]) msg.obj;
            // construct a string from the valid bytes in the buffer
//                String readMessage = new String(readBuf, 0, msg.arg1);
//                mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
            break;
        case MESSAGE_DEVICE_NAME:
            // save the connected device's name
            mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
            Toast.makeText(getApplicationContext(), "Connected to "
                           + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
            break;
        case MESSAGE_TOAST:
            Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                           Toast.LENGTH_SHORT).show();
            break;
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mCmdSendService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupBT();
            } else {
                // User did not enable Bluetooth or an error occured
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.connect:
            // Launch the DeviceListActivity to see devices and do scan
            Intent serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
            return true;
        case R.id.disconnect:
            // Stop the Bluetooth chat services
            if (mCmdSendService != null) mCmdSendService.stop();
        	return true;
        }
        return false;
    }

    private class MainView extends View {
        int disWidth;
        int disHeight;
        int width;
        int height;
        int X_COUNT = 3;
        int Y_COUNT = 21;

        private Paint mPaint;
        private Rect mRect1;
        private Rect mRect2;
        
        int m1;
        int m2;
        int id1;
        int id2;
        int sendm1;
        int sendm2;

        public MainView(Context context) {
            super(context);
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        	mPaint.setColor(Color.argb(255, 255, 255, 255));

        	mRect1 = new Rect();
            mRect2 = new Rect();

            m1 = 0;
            m2 = 0;
        }

        protected void onSizeChanged(int w, int h, int oldw, int oldh){
            disWidth = w;
            disHeight = h;
            width = disWidth/X_COUNT;
            height = disHeight/Y_COUNT;
        }
 
        @Override
        protected void onDraw(Canvas canvas) {

            canvas.drawLine(width, 0, width, disHeight,mPaint);
            canvas.drawLine(width*2, 0, width*2, disHeight,mPaint);

            for(int iy = 0;iy < Y_COUNT+1;iy++){
                canvas.drawLine(0, height*iy, width, height*iy,mPaint);
                canvas.drawLine(width*2, height*iy, disWidth, height*iy,mPaint);
            }

            mRect1.set(0, height*(m1+10), width, height*((m1+10)+1));
            canvas.drawRect(mRect1, mPaint);

            mRect2.set(width*2, height*(m2+10), disWidth, height*((m2+10)+1));
            canvas.drawRect(mRect2, mPaint);
        }

        public void TimerSendCmd() {
    		SendCmd(m2,m1);
        }

        public void SendCmd(int m1, int m2) {
    		if((sendm1 != m1)||(sendm2 != m2)){
    			sendMessage(String.format("m%+03d%+03d", m1*-1,m2*-1));
        		sendm1 = m1;
        		sendm2 = m2;
    		}        	
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {

        	int eAction = ev.getAction() & MotionEvent.ACTION_MASK;
        	int eActionId = (ev.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >> MotionEvent.ACTION_POINTER_ID_SHIFT;
	        int pointerCount = ev.getPointerCount();

        	if(eAction == MotionEvent.ACTION_UP){
    			m1 = 0;
    			m2 = 0;
        		TimerSendCmd();
		        stoptimer();
		   }else if(eAction == MotionEvent.ACTION_POINTER_UP ){
        		if(eActionId == id1){
        			m1 = 0;
        		}else if(eActionId == id2){
        			m2 = 0;
        		}
        	}else{
        	    if(eAction == MotionEvent.ACTION_DOWN){
                    starttimer();
        	    }
		        for (int p = 0; p < pointerCount; p++) {
		        	int x = (int)ev.getX(p);
		        	int y = (int)ev.getY(p);
//		        	Log.v("MotionEvent","action:"+ev.getAction()+" pointer "+ev.getPointerId(p)+": ("+ev.getX(p)+","+ev.getY(p)+")");
	
		        	if(x < width){
		        		m1 = (y / height)-10;
		        		if(m1 > 10)
		        		{
		        			m1 = 10;
		        		}
		        		id1 = p;
		        	}else if((x > width) && (x < width*2)){
		        		
		        	}else if((x > width*2) && (x < disWidth)){
		        		m2 = (y / height)-10;		        		
		        		if(m2 > 10)
		        		{
		        			m2 = 10;
		        		}
		        		id2 = p;
		        	}
		        }
        	}
//        	Log.v("motor",String.format("m%+03d%+03d", m1*-1,m2*-1));
	        invalidate();
        	return true;
        }
    }
}