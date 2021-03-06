/* 
Harmony Validation
File :              receive
Author :            T.Cattel cattel@iit.nrc.ca
Creation :          12 April 94
Last modification : 13 April 94
Description :       
- blocking msg reception
+ reabstraction from complete kernel
*/

#define _Receive(rid,id)\
  if\
  :: (id) ->\
       /* Receive specific */\
       _Disable();\
       correspondent[_Active] = id;\
       state[_Active] = Q_RECEIVER;\
       _Enable();\
       _Block_signal_processor(id);\
       _Disable();\
       _Convert_to_td(sender,correspondent[_Active]);\
       if\
       :: (!sender) ->\
            _Enable();\
            rid = 0\
       :: (sender) ->\
            state[_Active] = COPYING_MSG;\
            _Enable();\
            _Copy_msg();\
            state[_Active] = READY;\
            rid=correspondent[_Active]\
       fi\
  :: (!id) ->\
       /* Receive any */\
rec_try_again:\
       _Disable();\
       do\
       :: empty_send_q(_Active) ->\
            state[_Active]=RCV_BLOCKED;\
            _Enable();\
            _Block();\
            _Disable()\
       :: !empty_send_q(_Active) ->\
             break\
       od;\
       hdel_send_q(_Active,sender);\
       state[sender] = REPLY_BLOCKED;\
       correspondent[_Active]=sender;\
       state[_Active] = COPYING_MSG;\
       _Enable();\
       _Copy_msg();\
       if\
       :: (state[_Active]==ABORT_COPY_MSG) ->\
            goto rec_try_again\
       :: (state[_Active]!=ABORT_COPY_MSG) ->\
            state[_Active] = READY;\
            rid = correspondent[_Active]\
       fi\
  fi