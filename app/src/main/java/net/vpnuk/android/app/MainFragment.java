package net.vpnuk.android.app;

import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import de.blinkt.openvpn.api.APIVpnProfile;
import de.blinkt.openvpn.api.ExternalAppDatabase;
import de.blinkt.openvpn.api.ExternalOpenVPNService;
import de.blinkt.openvpn.api.IOpenVPNAPIService;
import de.blinkt.openvpn.api.IOpenVPNStatusCallback;

public class MainFragment extends Fragment implements View.OnClickListener, Handler.Callback {

    private static final String TAG = MainFragment.class.getSimpleName();
    private TextView mMyIp;
    private TextView mStatus;
    private Button mDisconnect;
    private Button mStartEmbeddedVpn;
    private Button mStartUUIDVpn;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_main, container, false);
        v.findViewById(R.id.disconnect_vpn_btn).setOnClickListener(this);
        v.findViewById(R.id.get_my_ip_btn).setOnClickListener(this);
        v.findViewById(R.id.start_embedded_vpn_btn).setOnClickListener(this);
        v.findViewById(R.id.start_uuid_vpn_btn).setOnClickListener(this);
        mStatus = (TextView) v.findViewById(R.id.status_text);
        mMyIp = (TextView) v.findViewById(R.id.my_ip_text);
        mDisconnect = (Button) v.findViewById(R.id.disconnect_vpn_btn);
        mStartEmbeddedVpn = (Button) v.findViewById(R.id.start_embedded_vpn_btn);
        mStartUUIDVpn = (Button) v.findViewById(R.id.start_uuid_vpn_btn);


        return v;

    }

    private static final int MSG_UPDATE_STATE = 0;
    private static final int MSG_UPDATE_MYIP = 1;
    private static final int START_PROFILE_EMBEDDED = 2;
    private static final int START_PROFILE_BYUUID = 3;
    private static final int ICS_OPENVPN_PERMISSION = 7;

    protected IOpenVPNAPIService mService=null;
    private Handler mHandler;

    private void startEmbeddedProfile()
    {
        try {
            InputStream conf = getActivity().getAssets().open("vpn.ovpn");
            InputStreamReader isr = new InputStreamReader(conf);
            BufferedReader br = new BufferedReader(isr);
            String config="";
            String line;
            while(true) {
                line = br.readLine();
                if(line == null)
                    break;
                config += line + "\n";
            }
            br.readLine();

            mService.startVPN(config);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mHandler = new Handler(this);
        bindService();
    }


    private IOpenVPNStatusCallback mCallback = new IOpenVPNStatusCallback.Stub() {
        /**
         * This is called by the remote service regularly to tell us about
         * new values.  Note that IPC calls are dispatched through a thread
         * pool running in each process, so the code executing here will
         * NOT be running in our main thread like most other things -- so,
         * to update the UI, we need to use a Handler to hop over there.
         */

        @Override
        public void newStatus(String uuid, String state, String message, String level)
                throws RemoteException {
            Message msg = Message.obtain(mHandler, MSG_UPDATE_STATE, state + "|" + message);
            msg.sendToTarget();

        }

    };


    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  We are communicating with our
            // service through an IDL interface, so get a client-side
            // representation of that from the raw service object.

            mService = IOpenVPNAPIService.Stub.asInterface(service);

            Log.d(TAG, "onServiceConnected: mService = " + (mService == null ? "null" : mService.toString()));

            // Add current application to the granted app database without confirmation dialog.

            new ExternalAppDatabase(getActivity()).addApp(getActivity().getPackageName());
            /* try {
                // Request permission to use the API
                Intent i = mService.prepare(getActivity().getPackageName());
                if (i!=null) {
                    startActivityForResult(i, ICS_OPENVPN_PERMISSION);
                } else {
                    onActivityResult(ICS_OPENVPN_PERMISSION, Activity.RESULT_OK,null);
                }

            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }*/
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mService = null;

            Log.d(TAG, "onServiceDisconnected");
        }
    };
    private String mStartUUID=null;

    private void bindService() {
        Intent intent = new Intent(getActivity(), ExternalOpenVPNService.class);

        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        getActivity().startService(new Intent(getActivity(), ExternalOpenVPNService.class));
    }

    protected void listVPNs() {

        try {
            List<APIVpnProfile> list = mService.getProfiles();
            String all="List:";
            for(APIVpnProfile vp:list) {
                all = all + vp.mName + ":" + vp.mUUID + "\n";
            }

            if(list.size()> 0) {
                Button b= mStartUUIDVpn;
                b.setOnClickListener(this);
                b.setVisibility(View.VISIBLE);
                b.setText(list.get(0).mName);
                mStartUUID = list.get(0).mUUID;
            }

        } catch (RemoteException e) {
            // TODO Auto-generated catch block
        }
    }

    private void unbindService() {
        getActivity().unbindService(mConnection);
    }

    @Override
    public void onStop() {
        super.onStop();
        unbindService();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.disconnect_vpn_btn:
                try {
                    mService.disconnect();
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                break;
            case R.id.get_my_ip_btn:
                // Socket handling is not allowed on main thread
                new Thread() {

                    @Override
                    public void run() {
                        try {
                            String myip = getMyOwnIP();
                            Message msg = Message.obtain(mHandler,MSG_UPDATE_MYIP,myip);
                            msg.sendToTarget();
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                    }
                }.start();

                break;
            case R.id.start_embedded_vpn_btn:
                try {
                    prepareStartProfile(START_PROFILE_EMBEDDED);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                break;
            default:
                break;
        }

    }

    private void prepareStartProfile(int requestCode) throws RemoteException {
        Intent requestpermission = mService.prepareVPNService();
        if(requestpermission == null) {
            onActivityResult(requestCode, Activity.RESULT_OK, null);
        } else {
            // Have to call an external Activity since services cannot used onActivityResult
            startActivityForResult(requestpermission, requestCode);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if(requestCode==START_PROFILE_EMBEDDED)
                startEmbeddedProfile();
            if(requestCode==START_PROFILE_BYUUID) {
                try {
                    mService.startProfile(mStartUUID);
                } catch (RemoteException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            listVPNs();
            try {
                mService.registerStatusCallback(mCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    String getMyOwnIP() throws UnknownHostException, IOException, RemoteException,
            IllegalArgumentException, IllegalAccessException, InvocationTargetException, NoSuchMethodException
    {
        String resp="";
        Socket client = new Socket();
        // Setting Keep Alive forces creation of the underlying socket, otherwise getFD returns -1
        client.setKeepAlive(true);

        client.connect(new InetSocketAddress("v4address.com", 23),20000);
        client.shutdownOutput();
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        while (true) {
            String line = in.readLine();
            if( line == null)
                return resp;
            resp+=line;
        }

    }

    @Override
    public boolean handleMessage(Message msg) {
        if(msg.what == MSG_UPDATE_STATE) {
            mStatus.setText((CharSequence) msg.obj);
        } else if (msg.what == MSG_UPDATE_MYIP) {

            mMyIp.setText((CharSequence) msg.obj);
        }
        return true;
    }
}
