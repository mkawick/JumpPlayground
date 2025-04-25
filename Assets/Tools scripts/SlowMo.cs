using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
//using static UnityEngine;

public class SlowMo : MonoBehaviour
{
    [SerializeField] Toggle slowMoButton;
    [SerializeField] Image background;
    public void OnButtonPressed()
    {
        if(slowMoButton.isOn)
        {
            // turn on
            background.color = new Color(0.5f, 1f, 0.5f);
            Time.timeScale = 0.35f;
        }
        else
        {
            // turn off
            background.color = new Color(0.55f, 0.55f, 0.55f);
            Time.timeScale = 1f;
        }
    }
}
