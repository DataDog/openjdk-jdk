#include "precompiled.hpp"

#ifdef LINUX

#include "runtime/os.hpp"
#include "runtime/os_perf.hpp"
#include "unittest.hpp"

namespace {
  class MockOS : public os {
  public:
    static bool can_access_proc;
    static DIR* opendir(const char* dirname) {
      fprintf(stdout, "opendir: %s\n", dirname);
      if (!MockOS::can_access_proc && strcmp(dirname, "/proc") != 0) {
        return nullptr;
      }
      return os::opendir(dirname);
    }
  };

  bool MockOS::can_access_proc = true;

  // Reincluding source files in the anonymous namespace unfortunately seems to
  // behave strangely with precompiled headers (only when using gcc though)
  #ifndef DONT_USE_PRECOMPILED_HEADER
  #define DONT_USE_PRECOMPILED_HEADER
  #endif

  #define os MockOS

  #include "runtime/os.hpp"
  #include "os_linux.hpp"
  #include "os_linux.inline.hpp"
  #include "os_linux.cpp"
  #include "os_perf_linux.cpp"

  #undef os
}

TEST_VM(TestPerfInterface, testRestrictedProc) {
  MockOS::can_access_proc = false;

  SystemProcess* processes = nullptr;
  int num_of_processes = 0;

  SystemProcessInterface spi;
  spi.system_processes(&processes, &num_of_processes);

  EXPECT_EQ(nullptr, processes);
  ASSERT_EQ(0, num_of_processes);
}

#endif // LINUX