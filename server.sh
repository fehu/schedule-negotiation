#!/bin/bash

su -c "mongod --noauth --rest --httpinterface --nojournal --cpu --noIndexBuildRetry"
