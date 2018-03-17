#ifndef _ADAPTER_H
#define _ADAPTER_H

void log(const char* messageTemplate, ...);

bool checkInitFunc();

void initFunc();

void setAchievementFunc(const char* achievementName);

void setAchievementProgressFunc(const char* achievementName, int current, int max);

void setStatFunc(const char* statName, int val);

void storeFunc();

void clearAchievementFunc(const char* achievementName);

void resetAllFunc();

#endif