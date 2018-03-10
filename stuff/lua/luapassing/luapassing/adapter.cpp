#include "adapter.h"
#ifdef _WINDOWS
#include "stdafx.h"
#else
#include <stdio.h>
#endif
#include <stdarg.h>

// Modify code here and insert actual code for all required actions -- begin

#define UserStatsObject void

UserStatsObject* getUserStatsObject() {
	return NULL;
}

void globalInit() {
	// Steam init here
}

void setAchievementUnsafe(UserStatsObject* pStats, const char* achievementName) {
	// Steam set achievement here
}

void setAchievementProgressUnsafe(UserStatsObject* pStats, const char* achievementName, int current, int max) {
	// Steam set achievement progress here
}

void storeUnsafe(UserStatsObject* pStats) {
	// Steam store stats here
}

void clearAchievementUnsafe(UserStatsObject* pStats, const char* achievementName) {
	// Steam clear achievement here
}

void resetAllUnsafe(UserStatsObject* pStats) {
	// Steam reset all stats here
}
// Modify code here and insert actual code for all required actions -- end

bool initDone = false;


void log(const char* format, ...) {
	FILE *log = fopen("logfile.txt", "at");
	if (!log) log = fopen("logfile.txt", "wt");
	if (!log) {
		printf("Can not open logfile.txt for writing! The message was:\n");
		va_list args;
		va_start(args, format);
		vprintf(format, args);
		va_end(args);
		return;   // bail out if we can't log
	}
	
	va_list args;
	va_start(args, format);
	vfprintf(log, format, args);
	va_end(args);

	fclose(log);
}

void initFunc() {
	globalInit();
	initDone = true;
}

bool checkInitFunc() {
	UserStatsObject* pStats = getUserStatsObject();
	return initDone && (pStats != NULL);
}

void setAchievementFunc(const char* achievementName) {
	UserStatsObject* pStats = getUserStatsObject();
	if (pStats != NULL) {
		setAchievementUnsafe(pStats, achievementName);
	} else {
		log("Error setting achievement %s: user stats object is undefined.\n", achievementName);
	}
}

void setAchievementProgressFunc(const char* achievementName, int current, int max) {
	UserStatsObject* pStats = getUserStatsObject();
	if (pStats != NULL) {
		setAchievementProgressUnsafe(pStats, achievementName, current, max);
	}
	else {
		log("Error setting achievement progress %s: user stats object is undefined.\n", achievementName);
	}
}

void storeFunc() {
	UserStatsObject* pStats = getUserStatsObject();
	if (pStats != NULL) {
		storeUnsafe(pStats);
	} else {
		log("Error storing achievements: user stats object is undefined.\n");
	}
}

void clearAchievementFunc(const char* achievementName) {
	UserStatsObject* pStats = getUserStatsObject();
	if (pStats != NULL) {
		clearAchievementUnsafe(pStats, achievementName);
	} else {
		log("Error clearing achievement %s: user stats object is undefined.\n", achievementName);
	}
}

void resetAllFunc() {
	UserStatsObject* pStats = getUserStatsObject();
	if (pStats != NULL) {
		resetAllUnsafe(pStats);
	} else {
		log("Error resetting achievements: user stats object is undefined.\n");
	}
}
