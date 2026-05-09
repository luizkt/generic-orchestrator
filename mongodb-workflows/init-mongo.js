db = db.getSiblingDB('generic-orchestrator');

db.createCollection('workflows');

db.workflows.createIndex({ "flowId": 1 }, { unique: true, sparse: true });

print('[init-mongo] Database generic-orchestrator and collection workflows initialized.');
