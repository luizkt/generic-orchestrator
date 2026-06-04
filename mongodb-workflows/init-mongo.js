db = db.getSiblingDB('generic-orchestrator');

db.createCollection('workflows');
db.createCollection('integrations');
db.createCollection('contracts');
db.createCollection('validations');

db.workflows.createIndex({ "flowId": 1, "version": 1 }, { unique: true, sparse: true });
db.integrations.createIndex({ "integrationId": 1, "version": 1 }, { unique: true, sparse: true });
db.contracts.createIndex({ "contractId": 1, "version": 1 }, { unique: true, sparse: true });
db.validations.createIndex({ "validationId": 1, "version": 1 }, { unique: true, sparse: true });

print('[init-mongo] Database generic-orchestrator initialized: workflows, integrations, contracts, validations.');
