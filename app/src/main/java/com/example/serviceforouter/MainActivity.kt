package com.example.serviceforouter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import com.example.serviceforouter.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    lateinit var messenger: Messenger
    lateinit var replyMessenger: Messenger
    lateinit var connectionMode: String
    lateinit var connection: ServiceConnection
    lateinit var binding: ActivityMainBinding
    var messengerJob: Job? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //서비스를 통신할 메신저 생성(저 아래 이너클래스 인커밍핸들러)
        replyMessenger = Messenger(IncomingHandler())

        //외부서비스와 접촉을 위한 Service
        connection = object : ServiceConnection {
            override fun onServiceConnected(p0: ComponentName?, iBinder: IBinder?) {
                messenger = Messenger(iBinder)
                val msg = Message()
                msg.what = 10
                //편지 안에 우체부를 넣음
                msg.replyTo = replyMessenger
                messenger.send(msg)
                connectionMode = "connectMessenger"
            }

            override fun onServiceDisconnected(p0: ComponentName?) {
                Log.d("ServiceForOuter", "messenger(iBinder) 객체받지 못함")
                connectionMode = "none"
            }
        }
        binding.messengerPlay.setOnClickListener {
            val intent = Intent("ACTION_SERVICE_MESSENGER")
            intent.setPackage("com.example.outerforservice")
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        binding.messengerStop.setOnClickListener {
            val msg = Message()
            msg.what = 20
            messenger.send(msg)
            unbindService(connection)
            connectionMode = "none"
        }
    }

    //아우터에서 받을 핸들러 이너클래스(자체가 액티비티라 컨택스트를 줄 필요가 없음)
    inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                //음원의 정보를 받을것
                100 -> {
                    Log.d("ServiceForOuter", "${msg.what} IncomingHandler")
                    val bundle = msg.obj as Bundle
                    bundle.getInt("duration")?.let {
                        when {
                            it > 0 -> {
                                binding.messengerProgress.max = it
                                //코루틴
                                val backgroundScope = CoroutineScope(Dispatchers.Default + Job())
                                messengerJob = backgroundScope.launch {
                                    while (binding.messengerProgress.progress < binding.messengerProgress.max) {
                                        delay(1000)
                                        binding.messengerProgress.incrementProgressBy(1000)
                                    }//while
                                    binding.messengerProgress.progress = 0
                                    connectionMode = "none"
                                    val msg = Message()
                                    msg.what = 20
                                    messenger.send(msg)
                                    unbindService(connection)
                                    messengerJob?.cancel()
                                }
                            }//it
                            else -> {
                                connectionMode = "none"
                                unbindService(connection)
                            }
                        }//end of when
                    }
                }
                // 다른 값을 주면 언바인드 요청(아우터는 스스로 끝낼 수 없기 때문에)
                200 -> {
                    connectionMode = "none"
                    val msg = Message()
                    msg.what = 20
                    messenger.send(msg)
                    unbindService(connection)
                    messengerJob?.cancel()
                    binding.messengerProgress.progress = 0
                }
                else -> {
                    super.handleMessage(msg)
                }
            }
        }
    }
}