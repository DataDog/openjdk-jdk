provider hotspot_jvmti {
  probe AllocObject__sample(char*, size_t, size_t);
};

#pragma D attributes Standard/Standard/Common provider hotspot_jvmti provider
#pragma D attributes Private/Private/Unknown provider hotspot_jvmti module
#pragma D attributes Private/Private/Unknown provider hotspot_jvmti function
#pragma D attributes Standard/Standard/Common provider hotspot_jvmti name
#pragma D attributes Evolving/Evolving/Common provider hotspot_jvmti args
