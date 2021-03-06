package pt.ulisboa.tecnico.grupo11.ubibike;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Messenger;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import pt.inesc.termite.wifidirect.SimWifiP2pDevice;
import pt.inesc.termite.wifidirect.SimWifiP2pDeviceList;
import pt.inesc.termite.wifidirect.SimWifiP2pInfo;
import pt.inesc.termite.wifidirect.SimWifiP2pManager;
import pt.inesc.termite.wifidirect.service.SimWifiP2pService;
import pt.inesc.termite.wifidirect.sockets.SimWifiP2pSocket;

public class Contacts extends AppCompatActivity implements SimWifiP2pManager.GroupInfoListener {
    public static ArrayAdapter<String> listAdapter;

    private SimWifiP2pSocket mCliSocket = null;
    private SimWifiP2pManager mManager = null;
    private SimWifiP2pManager.Channel mChannel = null;
    private Messenger mService = null;

    //  data stored for message sending methods
    private ProgressDialog progressDialog = null;
    private String currentUser = null;
    private String message = null;

    public static ArrayList<String> contacts = new ArrayList<>();
    public static ListView listView;

    public static String madeTransactions = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        progressDialog = new ProgressDialog(Contacts.this);
        progressDialog.setTitle("Sending");
        progressDialog.setMessage("Please wait.");

        ListView listView = (ListView) findViewById(R.id.listView);
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, contacts);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        });
        listView.setLongClickable(true);
        registerForContextMenu(listView);
        Intent intent = new Intent(this, SimWifiP2pService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.listView) {
            AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
            menu.setHeaderTitle(listAdapter.getItem(info.position));

            menu.add(0, 0, Menu.NONE, "Send points");
            menu.add(0, 1, Menu.NONE, "Send SMS");
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        final AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

        // create the alert box and text view
        final EditText edit = new EditText(this);
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(edit);
        builder.setPositiveButton("Send", null);
        builder.setNegativeButton("Cancel", null);
        builder.create();
        final AlertDialog dialog;

        switch (item.getItemId()) {
            case 0:
                // set the title and switch the keyboard to numeric
                edit.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                builder.setTitle("Send points");

                dialog = builder.create();

                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialogInterface) {
                        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        button.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View view) {
                                Editable value = edit.getText();
                                try {
                                    int points = Integer.parseInt(value.toString());
                                    if (points <= 0 || points > Tab.userPoints) {
                                        Toast.makeText(Contacts.this, "You can't send that amount of points", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    currentUser = listAdapter.getItem(info.position);
                                    long dateTime = new Date().getTime();
                                    message = "#P#" + Tab.username + "#" + currentUser + "#" + points + "#" + dateTime;
                                    MessageDigest md = MessageDigest.getInstance("MD5");
                                    md.update(message.getBytes(Charset.forName("UTF-8")));
                                    StringBuffer hexString = new StringBuffer();
                                    byte[] hash = md.digest();
                                    for (int i = 0; i < hash.length; i++) {
                                        if ((0xff & hash[i]) < 0x10) {
                                            hexString.append("0"
                                                    + Integer.toHexString((0xFF & hash[i])));
                                        } else {
                                            hexString.append(Integer.toHexString(0xFF & hash[i]));
                                        }
                                    }
                                    message += "#" + hexString.toString();

                                    mManager.requestGroupInfo(mChannel, Contacts.this);

                                    Tab.userPoints -= points;
                                    Tab.updatePoints = true;
                                    madeTransactions += message;
                                    message = madeTransactions;
                                    madeTransactions += ";;;";
                                    dialog.dismiss();
                                    progressDialog.show();
                                } catch (NumberFormatException e) {
                                    Toast.makeText(Contacts.this, "'" + value.toString() + "' is not a number.", Toast.LENGTH_SHORT).show();
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                        button = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                        button.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View view) {
                                dialog.dismiss();
                            }
                        });
                    }
                });

                dialog.show();
                break;

            case 1:
                // set the title
                builder.setTitle("Send SMS");
                dialog = builder.create();

                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialogInterface) {
                        Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        button.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View view) {
                                String smsText = edit.getText().toString();
                                if (!smsText.isEmpty()) {
                                    message = "#M#" + Tab.username + "#" + smsText;
                                    currentUser = listAdapter.getItem(info.position);
                                    mManager.requestGroupInfo(mChannel, Contacts.this);
                                    dialog.dismiss();
                                    progressDialog.show();
                                } else {
                                    Toast.makeText(Contacts.this, "The message is empty. Please write something.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });

                        button = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                        button.setOnClickListener(new View.OnClickListener() {

                            @Override
                            public void onClick(View view) {
                                dialog.dismiss();
                            }
                        });
                    }
                });
                dialog.show();
                break;
        }
        return true;
    }

    /*
            Connect with the target device
     */
    public class MessageSender extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                Log.d("WiFi Direct", "Connecting to '" + currentUser + "'");
                mCliSocket = new SimWifiP2pSocket(params[0],
                        Integer.parseInt(getString(R.string.port)));
                new SendCommTask().executeOnExecutor(
                        AsyncTask.THREAD_POOL_EXECUTOR,
                        message);
            } catch (Exception e) {
                Log.d("WiFi Direct", "Connection error - " + e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Contacts.this, "Couldn't deliver message. Make sure " + currentUser + " is within range", Toast.LENGTH_SHORT).show();
                        progressDialog.dismiss();
                    }
                });
            }
            return null;
        }

        @Override
        protected void onPostExecute(String result) {
        }
    }

    /*
     *     Sends a message to the user through WiFi direct
     */
    public class SendCommTask extends AsyncTask<String, String, Void> {
        @Override
        protected Void doInBackground(String... msg) {
            try {
                Log.d("WiFi Direct", "Sending message to '" + currentUser + "'");

                byte[] data = null;
                if (msg[0].startsWith("#P")) {
                    Log.d("WiFi Direct", "MSG[0] '" + msg[0] + "'");
                    // non-signed message
                    byte[] byteMessage = msg[0].getBytes("UTF-8");

                    // sign message
                    Home.signAlgorithm.initSign(Home.privateKey);
                    Log.e("KEYS", "PUBLICKEY SENDER: " + Base64.encodeToString(Home.publicKey.getEncoded(), Base64.DEFAULT));
                    Home.signAlgorithm.update(byteMessage);
                    byte[] signature = Home.signAlgorithm.sign();
                    msg[0] += ";;;" + Base64.encodeToString(signature, Base64.DEFAULT);
                    Log.d("WiFi Direct", "MSG[0] + Sig '" + msg[0] + "'");

                    // build entire message
                    data = msg[0].getBytes();

                } else if (msg[0].startsWith("#M")){
                    data = ("1"+msg[0]+"\n").getBytes();
                } else {
                    Log.d("SEND", "Unknown message: "+msg[0]);
                    return null;
                }

                String endOfMessage = "--END--\n";
                mCliSocket.getOutputStream().write(data);
                mCliSocket.getOutputStream().flush();
                Log.d("WiFi Direct", "SENDING- END");
                mCliSocket.getOutputStream().write(endOfMessage.getBytes());

                BufferedReader sockIn = new BufferedReader(
                        new InputStreamReader(mCliSocket.getInputStream()));
                sockIn.readLine();
            } catch (Exception e) {
                Log.d("WiFi Direct", "Connection error - " + e.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(Contacts.this, "Couldn't deliver message. Make sure " + currentUser + " is within range", Toast.LENGTH_SHORT).show();
                        progressDialog.dismiss();
                    }
                });
            }

            try {
                mCliSocket.close();
            } catch (IOException e) {
            }
            mCliSocket = null;
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            progressDialog.dismiss();
        }
    }

    /*
         When the group information is ready, find target user and send message
     */
    @Override
    public void onGroupInfoAvailable(SimWifiP2pDeviceList devices,
                                     SimWifiP2pInfo groupInfo) {
        Set<String> devicesInNetwork = groupInfo.getDevicesInNetwork();

        if (devicesInNetwork.isEmpty())
            Log.d("WiFi Direct", "There are no users in the network");
        for (String name : devicesInNetwork) {
            Log.d("WiFi Direct", "User '" + currentUser + "'");
            if (name.equals(currentUser)) {
                SimWifiP2pDevice device = devices.getByName(currentUser);
                Log.d("WiFi Direct", "Found user '" + currentUser + "' with ip " + device.getVirtIp());

                new MessageSender().executeOnExecutor(
                        AsyncTask.THREAD_POOL_EXECUTOR,
                        device.getVirtIp());
                return;
            }
        }

        Log.d("WiFi Direct", "Couldn't find '" + currentUser + "' within group.");
        Toast.makeText(Contacts.this, "Couldn't deliver message. Make sure " + currentUser + " is within range", Toast.LENGTH_SHORT).show();
        progressDialog.dismiss();
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        // callbacks for service binding, passed to bindService()

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            mManager = new SimWifiP2pManager(mService);
            mChannel = mManager.initialize(getApplication(), getMainLooper(), null);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService = null;
            mManager = null;
            mChannel = null;
        }
    };
}
