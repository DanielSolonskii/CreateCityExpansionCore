package com.example.examplemod.onboarding;
/** Presentation progress only. No rewards or gameplay permissions depend on client acknowledgements. */
public record OnboardingProgress(int seen,int completed,boolean skipped) {
 public static final int VERSION=1,COUNT=7,MASK=(1<<COUNT)-1;
 public enum Hint { WELCOME, MAP, COMMUNITY, PYLON, ROLE, PROJECTS, TERRITORY }
 public OnboardingProgress{seen&=MASK;completed&=MASK;}
 public static OnboardingProgress fresh(){return new OnboardingProgress(0,0,false);}
 public boolean eligible(int hint){return hint>=0&&hint<COUNT&&!skipped&&((seen|completed)&(1<<hint))==0;}
 public OnboardingProgress seen(int hint){return hint<0||hint>=COUNT?this:new OnboardingProgress(seen|(1<<hint),completed,skipped);}
 public OnboardingProgress completed(int hint){return hint<0||hint>=COUNT?this:new OnboardingProgress(seen,completed|(1<<hint),skipped);}
 public OnboardingProgress skip(){return new OnboardingProgress(seen,completed,true);}
}
