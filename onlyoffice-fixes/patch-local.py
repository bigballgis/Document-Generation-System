import json
with open('/etc/onlyoffice/documentserver/local.json', 'r') as f:
    cfg = json.load(f)
cfg['services']['CoAuthoring']['token']['outbox']['urlExclusionRegex'] = 'minio'
with open('/etc/onlyoffice/documentserver/local.json', 'w') as f:
    json.dump(cfg, f, indent=2)
print('patched successfully')
