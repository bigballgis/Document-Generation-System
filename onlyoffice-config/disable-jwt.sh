#!/bin/bash
# Disable JWT after Euro-Office generates its default config
sleep 5
if [ -f /etc/onlyoffice/documentserver/local.json ]; then
  python3 -c "
import json
with open('/etc/onlyoffice/documentserver/local.json','r') as f:
    c=json.load(f)
t=c.get('services',{}).get('CoAuthoring',{}).get('token',{}).get('enable',{})
if 'request' in t:
    t['request']['inbox']=False
    t['request']['outbox']=False
t['browser']=False
with open('/etc/onlyoffice/documentserver/local.json','w') as f:
    json.dump(c,f,indent=2)
print('JWT disabled successfully')
"
  supervisorctl restart all 2>/dev/null || true
fi
