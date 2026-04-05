package com.yshalsager.mafza.emergency.shell;

import android.os.Bundle;

interface IPrivilegedShellService {
    Bundle execute_argv(in String[] argv, int timeout_seconds);
    Bundle execute_raw_shell(String raw_shell, int timeout_seconds);
}
